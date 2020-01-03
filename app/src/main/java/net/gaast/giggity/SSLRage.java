/*
 * Giggity -- Android app to view conference/festival schedules
 * Copyright 2008-2011 Wilmer van der Gaast <wilmer@gaast.net>
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of version 2 of the GNU General Public
 * License as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA  02110-1301, USA.
 */

package net.gaast.giggity;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class SSLRage {
	/* This should probably go away soon. I wrote this >10y ago when LetsEncrypt wasn't a thing yet
	 * (and nobody had a clue who Edward Snowden was). By now you should either have proper certs or
	 * just use http:// if you couldn't be bothered.
	 */
	private class MitmMeTrustManager implements X509TrustManager {
		@Override
		public void checkClientTrusted(X509Certificate[] chain, String alg)
				throws CertificateException {
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String alg)
				throws CertificateException {
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return null;
		}
	}
	
	public static final SSLSocketFactory getSocketFactory() {
		TrustManager[] tm = new TrustManager[]{new SSLRage().new MitmMeTrustManager()};
		SSLContext ctx;
		try {
			ctx = SSLContext.getInstance("TLS");
			ctx.init(new KeyManager[0], tm, new SecureRandom());
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return null;
		} catch (KeyManagementException e) {
			e.printStackTrace();
			return null;
		}
		return ctx.getSocketFactory ();
	}
}
