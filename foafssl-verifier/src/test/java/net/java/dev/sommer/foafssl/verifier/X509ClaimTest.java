/*
 * New BSD license: http://opensource.org/licenses/bsd-license.php
 *
 * Copyright (c) 2010
 * Henry Story
 * http://bblfish.net/
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 *  this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 *  this list of conditions and the following disclaimer in the documentation
 *  and/or other materials provided with the distribution.
 * - Neither the name of bblfish.net nor the names of its contributors
 *  may be used to endorse or promote products derived from this software
 *  without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package net.java.dev.sommer.foafssl.verifier;

import net.java.dev.sommer.foafssl.cache.GraphCacheLookup;
import net.java.dev.sommer.foafssl.cache.MemoryGraphCache;
import net.java.dev.sommer.foafssl.claims.X509Claim;
import net.java.dev.sommer.foafssl.keygen.CertCreator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.security.KeyFactory;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.KeySpec;
import java.security.spec.RSAPublicKeySpec;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Henry Story
 */
public class X509ClaimTest {
    public static final String TEST_GOOD_FOAF_FILENAME = "dummy-foaf.rdf.xml";
    public static final String TEST_GOOD_FOAF_XHTML_FILENAME = "dummy-foaf.xhtml";
    public static final String TEST_GOOD_FOAF_HTML_FILENAME = "dummy-foaf.html";
    public static final String TEST_WRONG_FOAF_FILENAME = "dummy-foaf-wrong.rdf.xml";

    public static final String TEST_FOAF_LOCATION = "http://foaf.example.net/bruno";
    public static final URI TEST_WEB_ID_URI = URI.create(TEST_FOAF_LOCATION + "#me");
    public static final String TEST_CERT_FILENAME = "dummy-foafsslcert.pem";
    public static final URL TEST_FOAF_URL;
    X509Claim x509claim;

    final RSAPublicKey goodKey;

    public X509ClaimTest() throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        KeySpec keySpec = new RSAPublicKeySpec(
                new BigInteger(
                        "a4615390921b3d28b05b409280dbe6f34283a9fed892b670e111aadd6c951f58b101bf1c1fd7b5bb5"
                                + "493a9fa269ff1e3814747a24098c3e0b29b6d5a21eec655e1a60873803a2b7e9a158f25239c04608b0a32ed9c26ce6c"
                                + "1c2741426204b4351d02633d5c9a6bf7e387cd514d93445b37b4bb3ed85a114739c82e5e769ec277",
                        16), new BigInteger("65537"));
        goodKey = (RSAPublicKey) keyFactory.generatePublic(keySpec);
    }

    static {
        try {
            TEST_FOAF_URL = new URL(TEST_FOAF_LOCATION);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @Before
    public void setUp() throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        GraphCacheLookup.setCache(new MemoryGraphCache());

    }

    /**
     * Create a cert Valid for one Hour
     * 
     * @param foaf
     *            the local foaf document name
     * @return an X509Claim
     * @throws Exception
     */
    private X509Claim createOneHourCert(String foaf) throws Exception {
        CertCreator create = new CertCreator();
        create.addDurationInHours("1");
        create.setSubjectCommonName("TEST");
        URL webIdDoc = X509ClaimTest.class.getResource(foaf);
        webIdDoc = new URL(webIdDoc.getProtocol(), "localhost", webIdDoc.getFile());
        URL webId = new URL(webIdDoc, "#me");
        create.setSubjectWebID(webId.toString());
        create.setSubjectPublicKey(goodKey);
        create.generate();
        X509Certificate cert = create.getCertificate();
        x509claim = new X509Claim(cert);
        return x509claim;
    }

    @Test
    public void testGoodLocalFoafFile() throws Exception {
        X509Claim x509claim = createOneHourCert(TEST_GOOD_FOAF_FILENAME);
        assertTrue(x509claim.verify());
    }

    @Test
    public void testGoodLocalFoafXhtmlRDFaFile() throws Exception {
        X509Claim x509claim = createOneHourCert(TEST_GOOD_FOAF_XHTML_FILENAME);
        assertTrue(x509claim.verify());
    }

    @Test
    public void testGoodLocalFoafHtmlRDFaFile() throws Exception {
        X509Claim x509claim = createOneHourCert(TEST_GOOD_FOAF_HTML_FILENAME);
        assertTrue(x509claim.verify());

    }

    @Test
    public void testBadLocalFoafFile() throws Exception {
        X509Claim x509claim = createOneHourCert(TEST_WRONG_FOAF_FILENAME);
        assertFalse(x509claim.verify());
    }

}
