package net.gaast.giggity;

import android.content.Context;
import android.net.http.HttpEngine;
import android.net.http.HttpResponseCache;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Caching HTTP fetcher. */
public class Fetcher implements AutoCloseable {
	private Giggity app;
	private HttpURLConnection dlc;
	private Handler progressHandler;
	private long flen;
	private String type;
	private boolean isFresh_, fromCache_;

	private InputStream inStream;
	private BufferedReader inReader;
	static HttpResponseCache cache;
	private static HttpEngine httpEngine;

	private static final int httpCacheBytes = 32 * 1024 * 1024;

	public enum Source {
		REFRESH,           /* Ctrl-R, y'know */
		DEFAULT,           /* Check online (304 -> cache, and fail if we're offline). */
		CACHE_1D,          /* Get from cache but check for fresh once a day. */
		CACHE_1Y,          /* "Always" get from cache, if we can. */
		CACHE_ONLY,        /* Get from cache or fail. */
	}

	public static boolean init(Context ctx) {
		File dir = new File(ctx.getCacheDir(), "downloads");
		// Fixing a possible nullptr deref here but probably I can just delete this, the old
		// built in cache was replaced I think years ago?
		if (dir.exists() && dir.isDirectory() && dir.listFiles() != null) {
			Log.i("Fetcher", "Deleting old-style Fetcher cache");
			for (File f : dir.listFiles()) {
				if (!f.delete()) {

				}
			}
			dir.delete();
		}

		File httpResponseCacheDir = new File(ctx.getCacheDir(), "HttpResponseCache");

		if (Build.VERSION.SDK_INT >= 34) {
			try {
				httpEngine = new HttpEngine.Builder(ctx)
						.setEnableHttp2(true)
						// Cronet waits for QUIC for way too long, noticeably slows schedule loads.
						.setEnableQuic(false)
						.setEnableBrotli(true)
						.setStoragePath(httpResponseCacheDir.getAbsolutePath())
						.setEnableHttpCache(
								HttpEngine.Builder.HTTP_CACHE_DISK,
								httpCacheBytes
						)
						.setUserAgent("Giggity/" + BuildConfig.VERSION_NAME)
						.build();
				Log.i("Fetcher", "HttpEngine initialized successfully");
			} catch (Throwable t) {
				Log.w("Fetcher", "Failed to initialize HttpEngine, falling back: " + t);
			}
		}

		if (httpEngine == null) {
			Log.w("Fetcher", "Using old-fashioned HTTPURLConnection on old device.");
			try {
				// 32 MiB. No clue what I need, would've preferred something time-based but no such
				// option. But let's allow for some large PDFs or something without evicting schedules?
				cache = HttpResponseCache.install(httpResponseCacheDir, httpCacheBytes);
			} catch (IOException e) {
				Log.w("Fetcher", "HttpResponseCache.install: " + e);
				return false;
			}
		}
		return true;
	}

	public Fetcher(Giggity app_, String url, Source source) throws IOException {
		app = app_;

		Log.d("Fetcher", "Creating fetcher for " + url + " source=" + source);

		URL dl = new URL(url);
		if (httpEngine != null && Build.VERSION.SDK_INT >= 34) {
			dlc = (HttpURLConnection) httpEngine.openConnection(dl);
		} else {
			dlc = (HttpURLConnection) dl.openConnection();
			// HttpURLConnection doesn't do happy eyeballs (RFC 6555) so if a server is broken
			// on one of the address families we might get stuck forever without this timeout.
			// I hope 5s is long enough for lossy connections? (Good luck with the rest of the
			// fetch anyway..)
			dlc.setConnectTimeout(5000);
			dlc.addRequestProperty("User-Agent", "Giggity/" + BuildConfig.VERSION_NAME);
		}
		dlc.setInstanceFollowRedirects(true);

		if (source != Source.CACHE_ONLY && Looper.myLooper() == Looper.getMainLooper()) {
			throw new IOException("Fetcher: Should only allow Source.CACHE_ONLY in UI thread!");
		}
		if (cache == null || HttpResponseCache.getInstalled() != cache) {
			Log.w("Fetcher", "Cache appears to be missing?");
		}

		switch (source) {
			case DEFAULT:
				// No special headers needed, that's what default is about.. :)
				dlc.setUseCaches(true);
				break;
			case REFRESH:
				dlc.setUseCaches(false); // Bypasses local cache for network fetch
				dlc.setRequestProperty("Cache-Control", "max-age=0");
				break;
			case CACHE_1D:
				// Rely on cache with daily refreshes, regardless of what the server said.
				// (Would be nice to still trust the server if it returned a higher max-age but how?)
				dlc.setUseCaches(true);
				dlc.setRequestProperty("Cache-Control", "max-age=86400"); // 1 day
				break;
			case CACHE_1Y:
				// Add 1y to whatever the server said. Practically rely on cache forever.
				dlc.setUseCaches(true);
				dlc.setRequestProperty("Cache-Control", "max-stale=31536000"); // 1 year
				break;
			case CACHE_ONLY:
				dlc.setUseCaches(true);
				dlc.setRequestProperty("Cache-Control", "only-if-cached, max-stale=315360000");
				break;
		}

//		Log.d("Fetcher.Source", "" + source);
//		for (Map.Entry<String, java.util.List<String>> hd : dlc.getRequestProperties().entrySet()) {
//			for (String v : hd.getValue()) {
//				Log.d("Fetcher", "> " + hd.getKey() + ": " + v);
//			}
//		}

		// Request building ends here as we first try to access response fields.
		String status = dlc.getResponseCode() + " " + dlc.getResponseMessage();
		Log.d("Fetcher", "HTTP status " + status);
		// Log.d("Fetcher", "Req " + cache.getRequestCount() + " Net " + cache.getNetworkCount() + " Hit " + cache.getHitCount());
		String loc = dlc.getHeaderField("Location");
		if (loc != null) {
			Log.d("http-location", loc);
		}

		fromCache_ = false;
		isFresh_ = true;
		for (Map.Entry<String, List<String>> hd : dlc.getHeaderFields().entrySet()) {
			for (String v : hd.getValue()) {
				// Log.d("Fetcher", "< " + hd.getKey() + ": " + v);
				if (hd.getKey() == null) {
				} else if (hd.getKey().equals("Warning") && v.contains("Response is stale")) {
					isFresh_ = false;
				} else if (hd.getKey().equals("X-Android-Response-Source") && v.startsWith("CACHE")) {
					// CONDITIONAL_CACHE 304 shouldn't count as from cache.
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
		} else {
			throw new IOException("Download error: HTTP " + status);
		}
	}

	@Override
	public void close() throws IOException {
		// Mildly curious whether this function *might* lead to an unsent query unnecessarily
		// getting sent if cleaning up a never-finished query. Sadly I don't think there's a way
		// to check/prevent this AFAICT.
		if (inStream != null) {
			inStream.close();
		} else if (inReader != null) {
			inReader.close();
		} else if (dlc != null) {
			inStream = dlc.getInputStream();
			if (inStream != null) {
				inStream.close();
			}
		}
		dlc = null;
	}

	public void setProgressHandler(Handler handler) {
		progressHandler = handler;
	}

	public String getContentType() {
		if (dlc != null) {
			String s = dlc.getContentType();
			if (s != null) {
				s = s.replace(";.*", "").trim().toLowerCase();
			}
			return s;
		}
		return null;
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
		return inReader.lines().collect(Collectors.joining());
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
		if (cache != null) {
			cache.flush();
		}
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
