package wei_chih.utility;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.concurrent.ThreadLocalRandom;

import wei_chih.service.Config;

/**
 *
 * @author chienweichih
 */
public class Utils extends utility.Utils {
    private static final Logger LOGGER;
    
    static {
        LOGGER = Logger.getLogger(Utils.class.getName());
    }
    
    public static String digest(byte[] message) {
        return digest(message, Config.DIGEST_ALGORITHM);
    }
    
    /**
     * Digest a byte[] with a specific algorithm.
     * @param message
     * @param algorithm
     * @return the digest value
     */
    public static String digest(byte[] message, String algorithm) {
        String result = null;
        
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            
            result = Hex2Str(digest.digest(message));
        } catch (NoSuchAlgorithmException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
        
        return result;
    }
    
    public static String read(String fname) {
        String digest = null;
        
        try (FileReader fr = new FileReader(fname);
             BufferedReader br = new BufferedReader(fr)) {
            digest = br.readLine();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
        
        return digest;
    }
    
    public static void Serialize(File dest, Object ackStr) {
        try (FileOutputStream fout = new FileOutputStream(dest);
             ObjectOutputStream oos = new ObjectOutputStream(fout)) {   
            oos.writeObject(ackStr);
            oos.close();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }
    
    public static <T> T Deserialize(String src) {
        T obj = null;
        try (FileInputStream fin = new FileInputStream(src);
             ObjectInputStream ois = new ObjectInputStream(fin)) {
            obj = (T) ois.readObject();
            ois.close();
        } catch (IOException | ClassNotFoundException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
        return obj;
    }
    
    public static String getDataDirPath(String args) {
        String dataDirPath = Config.EMPTY_STRING;
        try {
            switch (args.charAt(args.length() - 1)) {
                case 'A':case 'a':
                    dataDirPath = new File(Config.DATA_A_PATH).getCanonicalPath();
                    break;
                case 'B':case 'b':
                    dataDirPath = new File(Config.DATA_B_PATH).getCanonicalPath();
                    break;
                case 'C':case 'c':
                    dataDirPath = new File(Config.DATA_C_PATH).getCanonicalPath();
                    break;
                case 'D':case 'd':
                    dataDirPath = new File(Config.DATA_D_PATH).getCanonicalPath();
                    break;
                default:
                    throw new java.lang.IllegalArgumentException();
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
        
        return dataDirPath;
    }
    
    public static String[] getTestFileName(String[] args) {
        if (args.length != 2) {
            System.err.println("NEED TWO ARGUMENT");
            return new String[]{Config.EMPTY_STRING};
        }

        String dataDirPath = getDataDirPath(args[0]);
        String testFileName = "";
        
        switch (dataDirPath.charAt(dataDirPath.length() - 1)) {
            case 'A':
                testFileName = Config.DATA_A_TESTFILE;
                break;
            case 'B':
                testFileName = Config.DATA_B_TESTFILE;
                break;
            case 'C':
                testFileName = Config.DATA_C_TESTFILE;
                break;
            case 'D':
                testFileName = Config.DATA_D_TESTFILE;
                break;
            default:
        }
                
        if (args[1].charAt(2) == '0') {
            testFileName += "/100";
        } else if (args[1].charAt(1) == '0') {
            testFileName += "/10";
        } else if (args[1].charAt(0) == '1') {
            testFileName += "/1";
        } else {
            System.err.println("ARGUMENT ERROR");
            return new String[]{Config.EMPTY_STRING};
        }
        
        if (Pattern.matches(".+[mM][bB]", args[1])) {
            testFileName += "MB.bin";
        } else if (Pattern.matches(".+[kK][bB]", args[1])) {
            testFileName += "KB.bin";
        } else {
           System.err.println("ARGUMENT ERROR");
           return new String[]{Config.EMPTY_STRING};
        }
        
        return new String[]{dataDirPath, testFileName};
    }
    
    public static ArrayList<String> randomPickupFiles(String folderPath, int number) throws FileNotFoundException {
        if (new File(folderPath).exists() == false) {
            throw new java.io.FileNotFoundException();
        }
        
        ArrayList<String> fileNames = new ArrayList<>();
        
        for (int i = 0; i < number; ++i) {
            File filePointer = new File(folderPath);
            while (filePointer.isDirectory()) {
                String[] fileList = filePointer.list();
                int childNum = fileList.length;
                int randomNum = ThreadLocalRandom.current().nextInt(0, childNum);
                
                filePointer = new File(filePointer.getAbsoluteFile() + File.separator + fileList[randomNum]);
            }
            fileNames.add(subPath(filePointer.getAbsolutePath()));
        }
                
        return fileNames;
    }
    
    private static String subPath(String path) {
        int indexOfHead = path.indexOf("Accounts");
        if (indexOfHead == -1) {
            return path;
        }
        return path.substring(indexOfHead + "Accounts/Account A".length());
    }
}
