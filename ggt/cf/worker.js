// ggt.gaa.st: serves the merged Giggity menu.json out of R2.
//
// Static files (index.html, nodatapolicy.html, .well-known/assetlinks.json)
// live in static/ and are served by Workers Static Assets before this code
// is ever invoked, see wrangler.toml. So all that's left here is:
//
//   /menu.json            Latest menu, written by the update-menu workflow.
//   /menu.json?rev=<sha>  Menu as of a specific (full) git commit id.
//
// Responses are kept in the (per-datacentre) edge cache so most requests
// never touch R2. The update-menu workflow purges /menu.json from that
// cache after every upload; EDGE_TTL is just the safety net in case that
// purge ever fails.

const REV_RE = /^[0-9a-f]{40}$/;
// All in seconds.
const MENU_TTL = 300; // What clients are told.
const EDGE_TTL = 3600; // How long our own edge cache may keep menu.json.
const REV_TTL = 31536000; // Never changes once written.

// Did the client already have this version? (cache.match() works this out
// by itself, this is just for responses fresh out of R2.)
function notModified(request, headers) {
	const inm = request.headers.get('if-none-match');
	if (inm !== null) {
		const strip = (tag) => tag.trim().replace(/^W\//, '');
		const etag = strip(headers.get('etag'));
		return inm.split(',').some((tag) => tag.trim() === '*' || strip(tag) === etag);
	}
	const ims = Date.parse(request.headers.get('if-modified-since'));
	return !isNaN(ims) && Date.parse(headers.get('last-modified')) <= ims;
}

async function serveMenu(request, env, ctx, key, cacheControl, edgeTtl) {
	const head = request.method === 'HEAD';
	const cache = caches.default;
	// Canonical URL, so random extra query parameters can't be used to
	// dodge (or fill up) the cache.
	const cacheUrl = new URL(request.url);
	cacheUrl.search = key === 'menu.json' ? '' : `?key=${key}`;

	// Passing the client's headers along lets the cache deal with
	// If-None-Match and friends, returning a 304 where appropriate.
	const cached = await cache.match(new Request(cacheUrl, { headers: request.headers }));
	if (cached) {
		// Put the client-facing Cache-Control back: the cached copy has the
		// edge TTL, or possibly the zone's Browser Cache TTL instead.
		const response = new Response(head ? null : cached.body, cached);
		response.headers.set('cache-control', cacheControl);
		return response;
	}

	const object = await env.MENU_BUCKET.get(key);
	if (!object) {
		return new Response("Don't have that version cached\n", { status: 404 });
	}

	const headers = new Headers({
		'content-type': 'application/json; charset=utf-8',
		'cache-control': cacheControl,
		'access-control-allow-origin': '*',
		'etag': object.httpEtag,
		'last-modified': object.uploaded.toUTCString(),
	});
	const response = new Response(object.body, { headers });
	const toCache = new Response(response.clone().body, response);
	toCache.headers.set('cache-control', `public, max-age=${edgeTtl}`);
	ctx.waitUntil(cache.put(cacheUrl, toCache));

	if (notModified(request, headers)) {
		return new Response(null, { status: 304, headers });
	}
	return head ? new Response(null, response) : response;
}

export default {
	async fetch(request, env, ctx) {
		const url = new URL(request.url);

		if (request.method !== 'GET' && request.method !== 'HEAD') {
			return new Response('Method not allowed\n', {
				status: 405,
				headers: { allow: 'GET, HEAD' },
			});
		}

		if (url.pathname === '/menu.json') {
			const rev = url.searchParams.get('rev');
			if (!rev || rev === 'HEAD') {
				return serveMenu(request, env, ctx, 'menu.json', `public, max-age=${MENU_TTL}`, EDGE_TTL);
			}
			if (!REV_RE.test(rev)) {
				return new Response('rev should be a full git commit id\n', { status: 400 });
			}
			return serveMenu(request, env, ctx, `menu-${rev}.json`,
			                 `public, max-age=${REV_TTL}, immutable`, REV_TTL);
		}

		// Not a static file either (or we wouldn't be here), so this is a 404.
		return env.ASSETS.fetch(request);
	},
};
