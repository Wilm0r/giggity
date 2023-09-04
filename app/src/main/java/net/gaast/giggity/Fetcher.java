package net.gaast.giggity;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.http.HttpResponseCache;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** Caching HTTP fetcher. */
public class Fetcher {
	private Giggity app;
	private HttpURLConnection dlc;
	private Handler progressHandler;
	private long flen;
	private String type;
	private boolean isFresh_, fromCache_;

	private InputStream inStream;
	private BufferedReader inReader;
	static HttpResponseCache cache;

	public enum Source {
		DEFAULT,           /* Check online (304 -> cache, and fail if we're offline). */
		CACHE_ONLY,        /* Get from cache or fail. */
		CACHE,             /* Get from cache, allow fetch if not available. */
		CACHE_IF_OFFLINE,  /* Check online if we're not offline, otherwise use cache. */
	}

	public static boolean init(Context ctx) {
		File dir = new File(ctx.getCacheDir(), "downloads");
		if (dir.exists() && dir.isDirectory()) {
			Log.i("Fetcher", "Deleting old-style Fetcher cache");
			for (File f : dir.listFiles()) {
				if (!f.delete()) {

				}
			}
			dir.delete();
		}

		try {
			dir = new File(ctx.getCacheDir(), "HttpResponseCache");
			// 32 MiB. No clue what I need, would've preferred something time-based but no such
			// option. But let's allow for some large PDFs or something without evicting schedules?
			cache = HttpResponseCache.install(dir, 32 * 1024 * 1024);
		} catch (IOException e) {
			Log.w("Fetcher", "HttpResponseCache.install: " + e);
			return false;
		}
		return true;
	}

	public Fetcher(Giggity app_, String url, Source sourcePref) throws IOException {
		this(app_, url, sourcePref, null);
	}

	public Fetcher(Giggity app_, String url, Source source, String type_) throws IOException {
		app = app_;
		type = type_; // TODO(http) die

		Log.d("Fetcher", "Creating fetcher for " + url + " source=" + source);

		NetworkInfo network = ((ConnectivityManager)
				app.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
		// Probably not quite as relevant now as when I first wrote Giggity.. Docs recommend to use
		// a listener instead but meh, don't care much + suddenly reloading when connectivity comes
		// back would be annoying UX as well, not worth it.
		boolean online = (network == null) || !network.isConnected();

		URL dl = new URL(url);
		dlc = (HttpURLConnection) dl.openConnection();
		dlc.setInstanceFollowRedirects(true);
		dlc.addRequestProperty("Accept-Encoding", "gzip");
		dlc.addRequestProperty("User-Agent", "Giggity/" + BuildConfig.VERSION_NAME);

		if (source != Source.CACHE_ONLY && Looper.myLooper() == Looper.getMainLooper()) {
			throw new IOException("Fetcher: Should only allow Source.CACHE_ONLY in UI thread!");
		}
		if (cache == null || HttpResponseCache.getInstalled() != cache) {
			Log.w("Fetcher", "Cache appears to be missing?");
		}

		if (source == Source.CACHE_IF_OFFLINE) {
			if (!online) {
				source = Source.CACHE;
			} else {
				source = Source.DEFAULT;
			}
		}
		switch (source) {
			case DEFAULT:
				dlc.addRequestProperty("Cache-Control", "max-age=0");
				break;
			case CACHE_ONLY:
				dlc.addRequestProperty("Cache-Control", "only-if-cached");
				// 10y (though apparently I could just omit the argument?)
				dlc.addRequestProperty("Cache-Control", "max-stale=" + (3650 * 24 * 60 * 60));
				break;
			case CACHE:
				dlc.addRequestProperty("Cache-Control", "max-stale=" + (365 * 24 * 60 * 60));  // 1y
				break;
		}

		Log.d("Fetcher", "HTTP status " + dlc.getHeaderField(0));
		String loc = dlc.getHeaderField("Location");
		if (loc != null) {
			Log.d("http-location", loc);
		}

		fromCache_ = false;
		isFresh_ = true;
		for (Map.Entry<String, java.util.List<String>> hd : dlc.getHeaderFields().entrySet()) {
			for (String v : hd.getValue()) {
//				Log.d("Fetcher", "" + hd.getKey() + ": " + v);
				if (hd.getKey() == null) {
				} else if (hd.getKey().equals("Warning") && v.contains("Response is stale")) {
					isFresh_ = false;
				} else if (hd.getKey().equals("X-Android-Response-Source") && v.contains("CACHE")) {
					fromCache_ = true;
				}
			}
		}
		Log.d("Fetcher", "fromCache=" + fromCache_ + " isFresh=" + isFresh_);

		if (dlc.getResponseCode() == HttpURLConnection.HTTP_OK) {
			inStream = dlc.getInputStream();

			// Set progress bar "goal" if we know it.
			if (dlc.getContentLength() > -1) {
				flen = dlc.getContentLength();
				inStream = new ProgressStream(inStream);
			}

			String enc = dlc.getContentEncoding();
			if (enc != null && enc.contains("gzip")) {
				inStream = new GZIPInputStream(inStream);
			}
		} else {
			throw new IOException("Download error: HTTP " + dlc.getHeaderField(0));
		}

//		if (inStream == null && fn.canRead()) {
//			/* We have no download stream and should use the cached copy (i.e. we're offline). */
//			flen = fn.length();
//			inStream = new ProgressStream(new FileInputStream(fn));
//			dlc = null;
//			if (source != Source.ONLINE)
//				source = Source.CACHE;
//		} else if (inStream == null) {
//			throw new IOException(app.getString(R.string.no_network_or_cached));
//		}
	}

	// Generate an (exportable) content:// URL to cached copy of this URL, if possible. Otherwise, null.
	public Uri cacheUri() {
//		if (source == Source.CACHE)
//			return FileProvider.getUriForFile(app, "net.gaast.giggity.paths", fn);
		return null;
	}

	private File fixMe_cacheFile(String url, boolean tmp) {
		// Need this for export
		String fn = Schedule.hashify(url);
		/* Filenames start to matter a bit: Using external viewers for navdrawer links for example.
		   Samsung's gallery is picky about filename extensions even when we're already passing a
		   MIME-type. So if we have a type, use it to add a filename extension (letters only). */
		if (type != null) {
			Matcher m = Pattern.compile("[a-z]+$").matcher(type);
			if (m.find() && !m.group().isEmpty()) {
				fn += "." + m.group();
			}
		}
		if (tmp) {
			fn = "." + fn + ".tmp";
		}
		File downloads = new File(app.getCacheDir(), "downloads");
		downloads.mkdirs();
		return new File(downloads, fn);
	}

	public void setProgressHandler(Handler handler) {
		progressHandler = handler;
	}

	// Export InputStream.
	public InputStream getStream() {
		if (inStream == null) {
			throw new RuntimeException("Already converted to BufferedReader!");
		}
		return inStream;
	}

	// Create a BufferedReader assuming charset=utf8 if necessary and return it. Streamer interface
	// should no longer be used after that.
	public BufferedReader getReader() {
		if (inReader == null) {
			inReader = new BufferedReader(new InputStreamReader(inStream, StandardCharsets.UTF_8));
			inStream = null;
		}
		return inReader;
	}

	/** Convenience slurper to just grab the whole file *as a String*. NO BINARIES! */
	public String slurp() throws IOException {
		String ret = "";
		char[] buf = new char[1024];
		int n;

		// Just to ensure inReader exists.
		getReader();
		while ((n = inReader.read(buf, 0, buf.length)) > 0)
			ret += new String(buf, 0, n);

		return ret;
	}

	public boolean fromCache() {
		return fromCache_;
	}

	public boolean isFresh() {
		return isFresh_;
	}

	/** Used to be mandatory in old implementation, vs cancel() to delete corrupt data (and keep
	 *  previous copy). Sadly we have lost that level now but I don't think it was used much anyway.
	 *  Just keep this as a cache flush checkpoint. */
	public void keep() {
		cache.flush();
	}

	/** Intermediate streamer to report progress to caller (mostly for UI). */
	private class ProgressStream extends InputStream {
		private InputStream in;
		private boolean waiting;
		private long offset;

		public ProgressStream(InputStream in_) {
			in = in_;
		}
		
		@Override
		public int read() throws IOException {
			if (!waiting)
				offset++;
			return in.read();
		}
		
		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int ret = in.read(b, off, len);
			if (!waiting) {
				offset += ret;
				if (progressHandler != null && flen > 0 && ret > 0) {
					int prog = (int) (100L * offset / flen);
					if (prog > 0)
						progressHandler.sendEmptyMessage((int) (100L * offset / flen));
				}
			}
			return ret;
		}
		
		@Override
		public int read(byte[] b) throws IOException {
			return read(b, 0, b.length);
		}
		
		@Override
		public void mark(int limit) {
			in.mark(limit);
			waiting = true;
		}
		
		@Override
		public void reset() throws IOException {
			in.reset();
			waiting = false;
		}
	}
}
