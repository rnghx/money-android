package local.money;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

final class SecretStore {
    private final Context context;
    private final String preferences,alias,aad;
    SecretStore(Context context) { this(context,"bank","money.sber.v1","local.money.sber.v1"); }
    SecretStore(Context context,String scope) { this(context,scope,"money."+scope+".v1","local.money."+scope+".v1"); }
    private SecretStore(Context context,String preferences,String alias,String aad) { this.context=context;this.preferences=preferences;this.alias=alias;this.aad=aad; }
    private javax.crypto.SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(alias))return (javax.crypto.SecretKey)store.getKey(alias,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    synchronized JSONObject load() throws Exception {
        String stored=context.getSharedPreferences(preferences,0).getString("access",null);if(stored==null)return null;
        String[] parts=stored.split(":");if(parts.length!=2)throw new Exception("storage");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
        cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        return new JSONObject(new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8));
    }
    synchronized void save(JSONObject access) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        String data=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(access.toString().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        if(!context.getSharedPreferences(preferences,0).edit().putString("access",data).commit())throw new Exception("storage");
    }
    synchronized void clear() { context.getSharedPreferences(preferences,0).edit().remove("access").commit(); }
}
