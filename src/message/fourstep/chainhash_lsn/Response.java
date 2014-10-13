package message.fourstep.chainhash_lsn;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPException;
import message.SOAPMessage;
import org.w3c.dom.NodeList;

/**
 *
 * @author Scott
 */
public class Response extends SOAPMessage {
    private static final long serialVersionUID = 20141013L;
    private final Request request;
    private final String result;
    private final String lastChainHash;
    
    public Response(Request req, String result, String hash) {
        super("response");
        
        this.request = req;
        this.result = result;
        this.lastChainHash = hash;
        
        add2Body("request", request.toString());
        add2Body("result", result);
        add2Body("chainhash", lastChainHash);
    }
    
    private Response(javax.xml.soap.SOAPMessage message) {
        super(message);
        
        NodeList body = getBody();
        
        this.request = Request.parse(body.item(0).getTextContent());
        this.result = body.item(1).getTextContent();
        this.lastChainHash = body.item(2).getTextContent();
    }
    
    public Request getRequest() {
        return request;
    }
    
    // for C&L
    public String getResult() {
        return result;
    }
    
    // for DC&L
    public String getClientLastChainHash() {
        return result;
    }
    
    public String getChainHash() {
        return lastChainHash;
    }
    
    public static Response parse(String receive) {
        InputStream stream;
        javax.xml.soap.SOAPMessage message = null;
        
        try {
            stream = new ByteArrayInputStream(receive.getBytes(StandardCharsets.UTF_8));
            message = MessageFactory.newInstance().createMessage(null, stream);
        } catch (SOAPException | IOException ex) {
            Logger.getLogger(SOAPMessage.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return new Response(message);
    }
}
