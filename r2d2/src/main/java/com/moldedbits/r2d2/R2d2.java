package com.moldedbits.r2d2;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.support.annotation.RequiresApi;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableEntryException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.x500.X500Principal;



/**
 * Created by shubham on 19/02/17.
 * Utils files to encrypt and decrypt sensitive information.
 * <p>
 * This contains different implementation for different versions of android.
 * <p>
 * for M and above we are using KeyGenParameterSpec for generation keys.
 * https://medium.com/@ericfu/securely-storing-secrets-in-an-android-application-501f030ae5a3#.c3xn3c4nb
 * <p>
 * for api level 18 and above we are using KeyPairGeneratorSpec
 * https://medium.com/@ericfu/securely-storing-secrets-in-an-android-application-501f030ae5a3#.c3xn3c4nb
 * http://www.androidauthority.com/use-android-keystore-store-passwords-sensitive-information-623779/
 * <p>
 * for 16 and 18 we are simply encrypting and decrypting the data
 * http://nelenkov.blogspot.in/2012/04/using-password-based-encryption-on.html
 */

public class R2d2 {

    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String AES_MODE = "AES/GCM/NoPadding";
    private static final String FIXED_IV = "fixed_direct";
    private static final String UTF = "UTF-8";
    private static final String AES = "AES";
    private KeyStore keyStore;
    private final Context context;

    private static String TAG = "R2D2";

    private String keyAlias;

    /**
     * Constructor It initializes and decides whether the android version is after M (API level 23)
     * or before it. Accordingly it generates a
     * random key according to the api level.
     * @param keyAlias Anybody can get the keys using this keyAlias so it is highly recommended
     *                  to get this either from user or from api.
     */
    public R2d2(final Context context, String keyAlias) {
        this.context = context;
        this.keyAlias = keyAlias;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            generateKeyStoreM();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            generateKeyStoreJ();
        }
    }

    /**
     * This handles for the api version after M (API level 23).
     * It uses KeyGenParameterSpec API which is included only for API levels 23 and higher.
     * This method generates a key if already a key with the same name is not already generated by the keystore.
     * The keystore in this case uses AES algorithm mode of encryption.
     * This is a symmetric mode of encryption i.e it uses same key both for encryption and decryption.
     */

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void generateKeyStoreM() {
        try {
            keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
            keyStore.load(null);
            if (!keyStore.containsAlias(keyAlias)) {
                KeyGenerator keyGenerator;

                keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);

                keyGenerator.init(
                        new KeyGenParameterSpec.Builder(keyAlias,
                                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                                .setRandomizedEncryptionRequired(false)
                                .build());
                keyGenerator.generateKey();
            }
        } catch (IOException | NoSuchAlgorithmException | CertificateException
                | KeyStoreException | NoSuchProviderException
                | InvalidAlgorithmParameterException e) {
            Log.e(TAG,"", e);
        }
    }

    /**
     * This encrypts the input for the api version after M (API level 23).
     * This method takes in the input which is to be encrypted.
     * Then the key generated above is retrieved from the key store.
     * The input is encrypted to cipher text using this key which is then returned finally.
     */

    @TargetApi(Build.VERSION_CODES.M)
    private String encryptDataM(String input) {
        try {
            byte[] bytes = input.getBytes(UTF);
            SecretKey key = (SecretKey) keyStore.getKey(keyAlias, null);

            Cipher c = Cipher.getInstance(AES_MODE);
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, FIXED_IV.getBytes()));
            byte[] encodedBytes = c.doFinal(bytes);
            return Base64.encodeToString(encodedBytes, Base64.DEFAULT);
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException
                | InvalidKeyException | InvalidAlgorithmParameterException
                | NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException
                | UnsupportedEncodingException e) {
            Log.e(TAG,"", e);
        }
        return null;
    }

    /**
     * This decrypts the input for the api version after M (API level 23).
     * This method takes in the input which is to be decrypted.
     * Then the key generated above is retrieved from the key store.
     * The input is decrypted to plain text using this key which is then returned finally.
     */

    @RequiresApi(api = Build.VERSION_CODES.M)
    private String decryptDataM(String input) {
        try {
            byte[] decode = Base64.decode(input, Base64.DEFAULT);

            //byte[] bytes =
            Cipher c = Cipher.getInstance(AES_MODE);
            SecretKey key = (SecretKey) keyStore.getKey(keyAlias, null);
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, FIXED_IV.getBytes()));
            byte[] decodedBytes = c.doFinal(decode);
            return new String(decodedBytes, UTF);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | BadPaddingException
                | UnrecoverableKeyException | KeyStoreException | IllegalBlockSizeException
                | InvalidAlgorithmParameterException | InvalidKeyException
                | UnsupportedEncodingException e) {
            Log.e(TAG,"", e);
        }
        return null;
    }

    /**
     * This handles for the api version before M i.e for API levels greater than or equal to 18 and less than 23.
     * It uses KeyPairGeneratorSpec API.
     * This method generates a key if already a key with the same name is not already generated by the keystore.
     * The keystore in this case uses RSA algorithm.
     * This is a asymmetric mode of encryption i.e it uses separate keys for encryption and decryption.
     * It generates a pair of public and private key.
     * For encryption it uses public key whereas for decryption it uses private key.
     */

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void generateKeyStoreJ() {
        try {
            keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
            keyStore.load(null);
            if (!keyStore.containsAlias(keyAlias)) {
                Calendar start = Calendar.getInstance();
                Calendar end = Calendar.getInstance();
                end.add(Calendar.YEAR, 20);
                KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(context)
                        .setAlias(keyAlias)
                        .setSubject(new X500Principal("CN=Sample Name, O=Android Authority"))
                        .setSerialNumber(BigInteger.ONE)
                        .setStartDate(start.getTime())
                        .setEndDate(end.getTime())
                        .build();
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", ANDROID_KEY_STORE);
                generator.initialize(spec);
                generator.generateKeyPair();
            }
        } catch (NoSuchAlgorithmException | KeyStoreException | NoSuchProviderException
                | InvalidAlgorithmParameterException | CertificateException | IOException e) {
            Log.e(TAG,"", e);
        }
    }

    /**
     * Encrypt the input and returns the encrypted value. This delegates the work to
     * corresponding functions according to api level.
     *
     * @param input data to be encrypted
     * @return encrypted data
     */
    public String encryptData(String input) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return encryptDataM(input);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return encryptDataJ(input);
        } else {
            return encryptDataDefault(input);
        }
    }

    /**
     * Decrypt the input value and returns the decrypted value. This delegates the work
     * to corresponding functions according to the api level.
     *
     * @param input data to be decrypted
     * @return decrypted data
     */
    public String decryptData(String input) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return decryptDataM(input);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return decryptDataJ(input);
        } else {
            return decryptDataDefault(input);
        }
    }

    /**
     * This encrypts the input for the api version before M i.e for API levels greater than or equal to 18 and less than 23.
     * This method takes in the input which is to be encrypted.
     * Then the public key generated from RSA algorithm above is retrieved from the key store.
     * The input is encrypted to cipher text using this public key which is then returned finally.
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private String encryptDataJ(String inputText) {
        try {
            KeyStore.PrivateKeyEntry privateKeyEntry
                    = (KeyStore.PrivateKeyEntry) keyStore.getEntry(keyAlias, null);
            RSAPublicKey publicKey = (RSAPublicKey) privateKeyEntry.getCertificate().getPublicKey();

            Cipher input = Cipher.getInstance("RSA/ECB/PKCS1Padding", "AndroidOpenSSL");
            input.init(Cipher.ENCRYPT_MODE, publicKey);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            CipherOutputStream cipherOutputStream = new CipherOutputStream(
                    outputStream, input);
            cipherOutputStream.write(inputText.getBytes(UTF));
            cipherOutputStream.close();

            byte[] vals = outputStream.toByteArray();
            return Base64.encodeToString(vals, Base64.DEFAULT);
        } catch (UnrecoverableEntryException | NoSuchAlgorithmException
                | InvalidKeyException | NoSuchProviderException
                | KeyStoreException | NoSuchPaddingException | IOException e) {
            Log.e(TAG,"", e);
        }
        return null;
    }

    /**
     * This decrypts the input for the api version before M i.e for API levels greater than or equal to 18 and less than 23..
     * This method takes in the input which is to be decrypted.
     * Then the private key generated from RSA algorithm above is retrieved from the key store.
     * The input is decrypted to plain text suing this private key which is then returned finally.
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private String decryptDataJ(String inputText) {
        try {
            KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(keyAlias, null);
            RSAPrivateKey privateKey = (RSAPrivateKey) privateKeyEntry.getPrivateKey();

            Cipher output = Cipher.getInstance("RSA/ECB/PKCS1Padding", "AndroidOpenSSL");
            output.init(Cipher.DECRYPT_MODE, privateKey);

            CipherInputStream cipherInputStream = new CipherInputStream(
                    new ByteArrayInputStream(Base64.decode(inputText, Base64.DEFAULT)), output);
            ArrayList<Byte> values = new ArrayList<>();
            int nextByte;
            while ((nextByte = cipherInputStream.read()) != -1) {
                values.add((byte) nextByte);
            }

            byte[] bytes = new byte[values.size()];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = values.get(i);
            }

            return new String(bytes, 0, bytes.length, UTF);
        } catch (UnrecoverableEntryException | NoSuchAlgorithmException | KeyStoreException
                | NoSuchPaddingException | InvalidKeyException | IOException
                | NoSuchProviderException e) {
            Log.e(TAG,"", e);
        }
        return null;
    }

    /**
     * This encrypts the input for the api versions lesser than 18.
     * This method takes in the input which is to be encrypted.
     * This generates a hash value using the SHA-1 hash function.
     * The plain text is encrypted to cipher text using the hash value and is returned finally.
     */
    private String encryptDataDefault(String input) {
        try {
            byte[] key = keyAlias.getBytes(UTF);
            MessageDigest sha = MessageDigest.getInstance("SHA-1");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16);
            byte[] inputByte = input.getBytes(UTF);
            Cipher c = Cipher.getInstance(AES);
            SecretKeySpec k = new SecretKeySpec(key, AES);
            c.init(Cipher.ENCRYPT_MODE, k);
            byte[] encryptedData = c.doFinal(inputByte);
            return Base64.encodeToString(encryptedData, Base64.DEFAULT);
        } catch (UnsupportedEncodingException | NoSuchAlgorithmException | InvalidKeyException
                | BadPaddingException | IllegalBlockSizeException | NoSuchPaddingException e) {
            Log.e(TAG,"", e);
        }
        return null;
    }

    /**
     * This decrypts the input for the api versions lesser than 18.
     * This method takes in the input which is to be decrypted.
     * This generates a hash value using the SHA-1 hash function.
     * The cipher text is decrypted to plain text using the hash value and is returned finally.
     */
    private String decryptDataDefault(String input) {
        try {
            byte[] key = keyAlias.getBytes(UTF);
            MessageDigest sha = MessageDigest.getInstance("SHA-1");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16);
            byte[] inputByte = Base64.decode(input, Base64.DEFAULT);
            Cipher c = Cipher.getInstance(AES);
            SecretKeySpec k = new SecretKeySpec(key, AES);
            c.init(Cipher.DECRYPT_MODE, k);
            byte[] decryptedData = c.doFinal(inputByte);
            return new String(decryptedData, UTF);
        } catch (UnsupportedEncodingException | NoSuchAlgorithmException | InvalidKeyException
                | BadPaddingException | IllegalBlockSizeException | NoSuchPaddingException e) {
            Log.e(TAG,"", e);
        }
        return null;
    }
}
