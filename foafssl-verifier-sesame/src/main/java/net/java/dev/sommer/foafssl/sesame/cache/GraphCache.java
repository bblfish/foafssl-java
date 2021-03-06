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

package net.java.dev.sommer.foafssl.sesame.cache;

import java.io.File;
import java.io.FileOutputStream;
import java.util.logging.Level;

import net.java.dev.sommer.foafssl.claims.WebIdClaim;
import net.java.dev.sommer.foafssl.util.SafeInputStream;
import org.openrdf.model.URI;
import org.openrdf.model.ValueFactory;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.repository.sail.SailRepositoryConnection;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFParseException;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.cert.Certificate;
import java.util.logging.Logger;

import net.java.dev.sommer.foafssl.util.TeeInputStream;

/**
 * @author Henry Story
 */
public abstract class GraphCache {


    static final Logger log = Logger.getLogger(GraphCache.class.getName());
    /**
     * 1/4 MB max length of FOAF files read.
     */
    private static final int MAX_LENGTH = 256 * 1024;

    SailRepository sailRepo;

    protected SailRepository getSailRep() {
        return sailRepo;
    }

    protected ValueFactory getVF() {
        return getSailRep().getValueFactory();
    }

    /**
     * Given a URL this method fetches the contents of it from the cache or the
     * web.
     * <p/>
     * <p>
     * Really what I would like is for this to return a graph of information
     * (not as currently: a connector to the whole database
     * </p>
     * TODO: replace the WebId argument with an interface that takes something
     * and error message logs.
     *
     * @param webidClaim the WebId to fetch
     * @return the repository connection containing the graph of the resource
     */
    public SailRepositoryConnection fetch(WebIdClaim webidClaim) {
        java.net.URI webid = webidClaim.getWebId();
        String scheme = webid.getScheme();
        if (!("http".equals(scheme) || "https".equals(scheme) || "ftp".equals(scheme)
                || "ftps".equals(scheme) || "file".equals(scheme))) {
            /*
             * "file" is for debugging purposes the above could be made more
             * generic by tying it to the URLConnection lib or something.
             */
            webidClaim.fail("Cannot dereference " + scheme + " urls");
            /*
             * one could do some further logic somehow, by checking if any
             * trusted statements were made by trusted sources...
             */
            return null;
        }

        URL purl = null;
        URL base = null;
        try {
            purl = webid.toURL();
            base = new URL(purl.getProtocol(), purl.getHost(), purl.getPort(), purl.getFile());
        } catch (MalformedURLException e) {
            webidClaim.addProblem(new Error("WebId is not one we know to dereference ", e));
            /*
             * TODO: one could do other things: like look at trusted graphs...
             * but for the moment to keep things simple, we fail in any case one
             * would need trusted graphs for this to work, and without a large
             * number of users, it won't have much value
             */
            return null;
        }

        InputStream foafDocInputStream = null;
        RDFFormat rdfFormat = null;
        try {

            try {
					 //todo: This should be placed in a thread with a timeout in case the host is down, or due to DNS problems					 
                URLConnection conn;
                conn = purl.openConnection();

                if (conn instanceof HttpURLConnection) {

                    /*
                     * TODO: add the type of connection as metadata on this
                     * graph eg, whether this is a secure connection or not
                     * 
                     * TODO: note there is an rfc where HTTP connections can be
                     * secure too, not widely deployed but it would be nice if
                     * it were.
                     */

                    HttpURLConnection hconn = (HttpURLConnection) conn;
                    /*
                     * set by default to True, but might as well override
                     * instances here, in case a default is set somewhere else
                     * in the code.
                     */
                    hconn.setInstanceFollowRedirects(true);
                    hconn.setRequestProperty("Accept",
                            "application/rdf+xml;q=1.0, text/turtle;q=0.9, text/html;q=0.7, application/xhtml+xml;q=0.8");
                }
                conn.connect();

                if (conn instanceof HttpsURLConnection) {
                    Certificate[] serverCertificates = ((HttpsURLConnection) conn)
                            .getServerCertificates();
                    webidClaim.setServerCertificateChain(serverCertificates);
                }

                String mimeType = mimeType(conn.getContentType());
                rdfFormat = RDFFormat.forMIMEType(mimeType);
                foafDocInputStream = conn.getInputStream();

                // cache everything
                //TODO, make this a setable property, so one can decide to have it on or not
                if (false) {
                    File tmpF = File.createTempFile("temp.", "." + rdfFormat.getFileExtensions().get(0));
                    FileOutputStream cacheout = new FileOutputStream(tmpF);
                    log.log(Level.INFO, "Storing output to file {0}", tmpF.getCanonicalPath());
                    cacheout.write(purl.toString().getBytes());
                    cacheout.write("\r\n\r\n".getBytes());
                    foafDocInputStream = new TeeInputStream(foafDocInputStream, cacheout);
                }
            } catch (IOException e) {
                webidClaim.fail("could not connect to resource " + purl, e);
                return null;
            }

            URI foafdocUri = getVF().createURI(base.toString());
            SailRepositoryConnection repconn = null;

            try {
                repconn = getSailRep().getConnection();
            } catch (RepositoryException e) {
                webidClaim.fail("internal error. Could not connect to rdf repository", e);
            }
            try {
                SafeInputStream stream = new SafeInputStream(foafDocInputStream, MAX_LENGTH);
					 repconn.clear(foafdocUri);
                repconn.add(stream, base.toString(), rdfFormat, foafdocUri);
                webidClaim.setGraphName(base);
                if (stream.wasCutShort()) {
                    webidClaim.warn("Input from resource was cut off at " + stream.getMax()
                            + " Data could be missing.");
                }
            } catch (IOException e) {
                webidClaim.fail("Could not read input from resource " + purl, e);
                /*
                 * TODO: need not return null. It could try to continue and read
                 * from cache
                 */
                return null;
            } catch (RDFParseException e) {
                webidClaim.fail("Could not parse resource " + purl, e);
                /*
                 * TODO: need not return null. It could try to continue and read
                 * from cache
                 */
                return null;
            } catch (RepositoryException e) {
                webidClaim.fail("Internal error. Could not add information to repository", e);
                return null;
            }
            return repconn;
        } finally {
            if (foafDocInputStream != null) {
                try {
                    foafDocInputStream.close();
                } catch (IOException e) {

                }
            }
        }
    }

    /**
     * Get the mime type
     *
     * @param contentType the content type header
     * @return the basic mime type
     */
    private String mimeType(String contentType) {
        int i = contentType.indexOf(';');
        if (i > 0) {
            contentType = contentType.substring(0, i);
        }
        return contentType.trim();
    }

}
