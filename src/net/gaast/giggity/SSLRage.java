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
	/* SSL Rage, since SSLFFFFFUUUUUUUU would be too annoying to spell.
	 * 
	 * This tool needs to download schedules, sometimes from https servers.
	 * OSS events often don't have a lot of $$$$ so understandably they're
	 * not going to pay money for a proper certificate.
	 * 
	 * The result is this file, the hack required to make Java accept them.
	 * 'cause really, what'd be the benefit of MitM'ing this download?
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
		TrustManager tm[] = new TrustManager[] { new SSLRage().new MitmMeTrustManager() };
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
		return (SSLSocketFactory) ctx.getSocketFactory ();
	}

	/* For now I'm not using this one anymore, since IMHO getting the hostname
	 * of your certs right isn't that hard/expensive.
	 */
	final class TestVerifier implements javax.net.ssl.HostnameVerifier {
		@Override
		public boolean verify(String hostname, SSLSession session) {
			return true;
		}
	}
}
