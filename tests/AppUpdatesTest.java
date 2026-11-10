package local.money;
import java.net.URL;
import org.json.JSONObject;
public class AppUpdatesTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static JSONObject manifest()throws Exception{return new JSONObject().put("versionCode",11).put("versionName","0.11.0").put("apkUrl","https://github.com/rnghx/money-android/releases/download/v0.11.0/app.apk").put("sha256","a".repeat(64)).put("size",90000);}
 static void reject(JSONObject j)throws Exception{try{AppUpdates.validateManifest(j);throw new AssertionError("Accepted invalid manifest");}catch(java.io.IOException expected){}}
 public static void main(String[] args)throws Exception{
 check(AppUpdates.allowed(new URL("https://github.com/rnghx/money-android")));
 check(AppUpdates.allowed(new URL("https://release-assets.githubusercontent.com/path")));
 check(!AppUpdates.allowed(new URL("http://github.com/path")));
 check(!AppUpdates.allowed(new URL("https://github.com.evil.example/path")));
 check(!AppUpdates.allowed(new URL("https://user@github.com/path")));
 check(!AppUpdates.allowed(new URL("https://github.com:444/path")));
 AppUpdates.validateManifest(manifest());
 reject(manifest().put("versionCode",-1));reject(manifest().put("size",104857601));reject(manifest().put("size",0));reject(manifest().put("sha256","bad"));reject(manifest().put("apkUrl","https://github.com/other/app/releases/download/v1/app.apk"));reject(manifest().put("apkUrl","http://github.com/rnghx/money-android/releases/download/v1/app.apk"));
 System.out.println("App update manifest/host checks passed.");
 }
}
