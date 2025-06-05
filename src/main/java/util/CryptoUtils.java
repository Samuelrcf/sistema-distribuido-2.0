package util;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class CryptoUtils {
    private static final String ALGORITHM = "AES";
    private static final String CHAVE = "56ce905b-eb57-4b"; 

    public static String encrypt(String valor) throws Exception {
        SecretKeySpec key = new SecretKeySpec(CHAVE.getBytes(), ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(valor.getBytes());
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public static String decrypt(String valorCriptografado) throws Exception {
        SecretKeySpec key = new SecretKeySpec(CHAVE.getBytes(), ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decoded = Base64.getDecoder().decode(valorCriptografado);
        byte[] decrypted = cipher.doFinal(decoded);
        return new String(decrypted);
    }
}

