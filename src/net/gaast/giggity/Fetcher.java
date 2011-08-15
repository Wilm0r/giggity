package net.gaast.giggity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HttpsURLConnection;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.util.Log;

/** Caching HTTP fetcher. */
public class Fetcher {
	private Giggity app;
	private File fn, fntmp = null;
	private URLConnection dlc;
	private BufferedReader in; 
	
	public Fetcher(Giggity app_, String url, boolean online) throws IOException {
		app = app_;

		Log.d("Fetcher", "Creating fetcher for " + url + " online=" + online);
		
		fn = new File(app.getCacheDir(), "sched." + Schedule.hashify(url));
		fntmp = null;

		NetworkInfo network = ((ConnectivityManager)
				app.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo(); 
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(app);
		
		URL dl = new URL(url);
		dlc = dl.openConnection();
		
		try {
			/* Do HTTP stuff first, then HTTPS! Once we get a CastClassException, we stop. */
			HttpURLConnection.setFollowRedirects(true);
			
			/* Disabled for now since I can't get the default User-Agent until after sending it.
			String ua = ((HttpURLConnection)dlc).getRequestProperty("User-Agent");
			((HttpURLConnection)dlc).setRequestProperty("User-Agent", "Giggity, " + ua);
			*/
			
			((HttpURLConnection)dlc).addRequestProperty("Accept-Encoding", "gzip");
			
			if (fn.canRead() && fn.lastModified() > 0) {
				/* TODO: Not sure if it's a great idea to use inode metadata to store
				 * modified-since data, but it works so far.. */
				dlc.setIfModifiedSince(fn.lastModified());
			}
			
			if (!pref.getBoolean("strict_ssl", false))
				((HttpsURLConnection)dlc).setSSLSocketFactory(SSLRage.getSocketFactory());
		} catch (ClassCastException e) {
			/* It failed. Maybe we're HTTP only? Maybe even FTP? */
		}
		
		if (online && network != null && network.isConnected()) {
			int status;
			try {
				status = ((HttpURLConnection)dlc).getResponseCode();
				Log.d("Fetcher", "HTTP status "+status);
			} catch (ClassCastException e) {
				/* Assume success if this isn't HTTP.. */
				status = 200;
			}
			if (status == 200) {
				fntmp = new File(app.getCacheDir(), "tmp." + Schedule.hashify(url));
				Writer copy = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fntmp)));
				Reader rawin;
				String enc = dlc.getContentEncoding();
				Log.d("Fetcher", "HTTP encoding " + enc);
				if (enc != null && enc.contains("gzip"))
					rawin = new InputStreamReader(new GZIPInputStream(dlc.getInputStream()), "utf-8");
				else
					rawin = new InputStreamReader(dlc.getInputStream(), "utf-8");
				in = new TeeReader(rawin, copy, 4096);
			} else if (status == 304) {
				Log.i("Fetcher", "HTTP 304, using cached copy");
				/* Just continue, in = null so we'll read from cache. */
			} else {
				throw new IOException("Download error: " + dlc.getHeaderField(0));
			}
		}

		if (in == null && fn.canRead()) {
			/* We have no download stream and should use the cached copy (i.e. we're offline). */
			in = new BufferedReader(new InputStreamReader(new FileInputStream(fn)));
			dlc = null;
		} else if (in == null) {
			throw new IOException("No network connection or cached copy available.");
		}
	}
	
	
	public BufferedReader getReader() {
		return in;
	}
	
	public String slurp() {
		String ret = "";
		char[] buf = new char[1024];
		int n;
		
		try {
			while ((n = in.read(buf, 0, buf.length)) > 0)
				ret += new String(buf, 0, n);
		} catch (IOException e) {
			e.printStackTrace();
			ret = null;
		}
		return ret;
	}

	/** If the file is usable, keep it cached. */
	public void keep() {
		try {
			/* Close it now because otherwise (I'm just guessing) we may still flush a buffer and reset mtime. */
			in.close();
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
	
	/* I want to keep local cached copies of schedule files. This reader makes that easy. */
	private class TeeReader extends BufferedReader {
		Writer writer;
		boolean waiting;
		
		public TeeReader(Reader in, Writer out, int buf) {
			super(in, buf);
			writer = out;
		}
		
		@Override
		public void mark(int limit) throws IOException {
			super.mark(limit);
			waiting = true;
		}
		
		@Override
		public void reset() throws IOException {
			super.reset();
			waiting = false;
		}

		@Override
		public int read(char[] buf, int off, int len) throws IOException {
			int st = super.read(buf, off, len);
			if (writer != null && !waiting && st > 0) {
				writer.write(buf, off, st);
			}
			return st;
		}
		
		@Override
		public String readLine() throws IOException {
			String ret;
			ret = super.readLine();
			if (writer != null && !waiting && ret != null)
				writer.write(ret + "\n");
			return ret;
		}
		
		@Override
		public void close() throws IOException {
			super.close();
			writer.close();
		}
	}
}
