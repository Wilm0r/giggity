package net.gaast.giggity;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.FileProvider;
import android.util.Log;

import org.apache.commons.io.input.TeeInputStream;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HttpsURLConnection;

/** Caching HTTP fetcher. */
public class Fetcher {
	private Giggity app;
	private File fn, fntmp = null;
	private URLConnection dlc;
	private Source source;
	private Handler progressHandler;
	private long flen;
	private String type;

	private InputStream inStream;
	private BufferedReader inReader;

	public enum Source {
		CACHE,			/* Get from cache or fail. */
		CACHE_ONLINE,	/* Get from cache, allow fetch if not available. */
		ONLINE_CACHE,	/* Check online if we can, otherwise use cache. */
		ONLINE,			/* Check online (304 -> cache). */
		ONLINE_NOCACHE,	/* Fetch online, ignore cached version. */
	}
	
	public Fetcher(Giggity app_, String url, Source prefSource) throws IOException {
		this(app_, url, prefSource, null);
	}

	public Fetcher(Giggity app_, String url, Source prefSource, String type_) throws IOException {
		app = app_;
		source = null;
		type = type_;

		Log.d("Fetcher", "Creating fetcher for " + url + " prefSource=" + prefSource);

		fn = cacheFile(url, false);
		fntmp = null;

		NetworkInfo network = ((ConnectivityManager)
				app.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo(); 
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(app);

		if ((prefSource == Source.ONLINE || prefSource == Source.ONLINE_NOCACHE) &&
		    (network == null || !network.isConnected())) {
			throw new IOException(app.getString(R.string.no_network));
		}
		
		URL dl = new URL(url);
		dlc = dl.openConnection();
		
		try {
			/* Do HTTP stuff first, then HTTPS! Once we get a CastClassException, we stop. */
			HttpURLConnection.setFollowRedirects(true);
			
			((HttpURLConnection)dlc).addRequestProperty("Accept-Encoding", "gzip");
			
			if (prefSource != Source.ONLINE_NOCACHE && fn.canRead() && fn.lastModified() > 0) {
				/* TODO: Not sure if it's a great idea to use inode metadata to store
				 * modified-since data, but it works so far.. */
				dlc.setIfModifiedSince(fn.lastModified());
			}
			
			if (!pref.getBoolean("strict_ssl", false))
				((HttpsURLConnection)dlc).setSSLSocketFactory(SSLRage.getSocketFactory());
		} catch (ClassCastException e) {
			/* It failed. Maybe we're HTTP only? Maybe even FTP? Heh well good luck! */
		}

		if (prefSource != Source.CACHE && !(fn.canRead() && prefSource == Source.CACHE_ONLINE) &&
		    network != null && network.isConnected()) {
			int status;
			try {
				status = ((HttpURLConnection)dlc).getResponseCode();
				Log.d("Fetcher", "HTTP status " + status);
			} catch (ClassCastException e) {
				/* Assume success if this isn't HTTP.. */
				status = 200;
			}
			if (status == 200) {
				inStream = dlc.getInputStream();

				if (dlc.getContentLength() > -1) {
					flen = dlc.getContentLength();
					inStream = new ProgressStream(inStream);
				}

				String enc = dlc.getContentEncoding();
				if (enc != null && enc.contains("gzip")) {
					inStream = new GZIPInputStream(inStream);
				}

				fntmp = cacheFile(url, true);
				OutputStream copy = new FileOutputStream(fntmp);
				inStream = new TeeInputStream(inStream, copy, true);  // true == close copy on close

				source = Source.ONLINE;
			} else if (status == 304) {
				Log.i("Fetcher", "HTTP 304, using cached copy");
				source = Source.ONLINE; /* We're reading cache, but 304 means it should be equivalent. */
				/* Just continue, inStream = null so we'll read from cache. */
			} else {
				throw new IOException("Download error: " + dlc.getHeaderField(0));
			}
		}

		if (inStream == null && fn.canRead()) {
			/* We have no download stream and should use the cached copy (i.e. we're offline). */
			flen = fn.length();
			inStream = new ProgressStream(new FileInputStream(fn));
			dlc = null;
			if (source != Source.ONLINE)
				source = Source.CACHE;
		} else if (inStream == null) {
			throw new IOException(app.getString(R.string.no_network_or_cached));
		}
	}

	// Generate an (exportable) content:// URL to cached copy of this URL, if possible. Otherwise, null.
	public Uri cacheUri() {
		if (source == Source.CACHE)
			return FileProvider.getUriForFile(app, "net.gaast.giggity.paths", fn);
		return null;
	}

	private File cacheFile(String url, boolean tmp) {
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

	// Export InputStream. Will return null if you've requested a Reader at least once.
	public InputStream getStream() {
		return inStream;
	}

	// Create a BufferedReader assuming charset=utf8 if necessary and return it. Streamer interface
	// should no longer be used after that.
	public BufferedReader getReader() {
		if (inReader == null) {
			try {
				inReader = new BufferedReader(new InputStreamReader(inStream, "utf-8"));
				// I don't know whether Readers take full ownership of Streamers but let's just be
				// sure and only ever use one of the interfaces.
				inStream = null;
			} catch (UnsupportedEncodingException e) {
				// If Java stops support utf-8 we're going to have a bad time.
			}
		}
		return inReader;
	}
	
	public String slurp() {
		String ret = "";
		char[] buf = new char[1024];
		int n;

		// Just to ensure inReader exists.
		getReader();
		
		try {
			while ((n = inReader.read(buf, 0, buf.length)) > 0)
				ret += new String(buf, 0, n);
		} catch (IOException e) {
			e.printStackTrace();
			ret = null;
		}
		return ret;
	}
	
	public Source getSource() {
		return source;
	}

	/** If the file is usable, keep it cached. */
	public void keep() {
		try {
			/* Close it now because otherwise (I'm just guessing) we may still flush a buffer and reset mtime. */
			if (inReader != null) {
				inReader.close();
			} else {
				inStream.close();
			}
		} catch (IOException e) {
		}
		
		if (fntmp != null)
			/* Save the cached copy (overwrite the old one, if any). */
			fntmp.renameTo(fn);
		
		/* Store last-modified date so we can cache more efficiently. */
		if (dlc != null && dlc.getLastModified() > 0)
			fn.setLastModified(dlc.getLastModified());
	}
	
	/** If the file was corrupt in any way, remove it. */
	public void cancel() {
		if (fntmp != null)
			/* Delete the cached copy we were writing. */
			fntmp.delete();
		else
			/* Delete the cached copy we were using. */
			fn.delete();
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
		public int read(byte b[], int off, int len) throws IOException {
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
		public int read(byte b[]) throws IOException {
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
