package voting_pov.service;

import java.io.File;

/**
 *
 * @author chienweichih
 */
public interface Config extends service.Config {
    public int VOTING_SERVICE_PORT_1 = 3011;
    public int VOTING_SERVICE_PORT_2 = 3012;
    public int VOTING_SERVICE_PORT_3 = 3013;
    public int VOTING_SERVICE_PORT_4 = 3014;
    public int VOTING_SERVICE_PORT_5 = 3015;
    
    public String DATA_DIR_PATH = ".." + File.separator + "Accounts" + File.separator + "Account B";
    
    public String EMPTY_STRING = " ";
    public String DOWNLOAD_FAIL = "download fail";
    public String UPLOAD_FAIL = "upload fail";
    public String AUDIT_FAIL = "audit fail";
    public String OP_TYPE_MISMATCH = "operation type mismatch";
    public String WRONG_OP = "wrong op";
}