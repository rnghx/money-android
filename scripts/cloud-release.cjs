const fs=require('node:fs'),cp=require('node:child_process'),crypto=require('node:crypto'),path=require('node:path');
const repo='rnghx/money-android',token=process.env.GH_TOKEN;
async function api(route,method='GET',data){const r=await fetch('https://api.github.com'+route,{method,headers:{Authorization:'Bearer '+token,'User-Agent':'money-cloud-release',Accept:'application/vnd.github+json',...(data?{'Content-Type':'application/json'}:{})},body:data?JSON.stringify(data):undefined});if(!r.ok)throw Error('GitHub HTTP '+r.status+' '+route);return r.json();}
(async()=>{
 if(!token)throw Error('Workflow token missing');
 const metadata=JSON.parse(fs.readFileSync('app/build/outputs/apk/debug/output-metadata.json')).elements[0];
 const version=metadata.versionName,code=metadata.versionCode,tag='v'+version;
 if(!/^\d+\.\d+\.\d+$/.test(version)||!Number.isSafeInteger(code))throw Error('Invalid version');
 const latest=await api('/repos/'+repo+'/releases/latest');
 const manifestAsset=latest.assets.find(a=>a.name==='update.json');if(!manifestAsset)throw Error('Previous manifest missing');
 const previous=await(await fetch(manifestAsset.browser_download_url)).json();if(code<=previous.versionCode)throw Error('Increase versionCode and versionName before releasing');
 const apk='app/build/outputs/apk/debug/'+metadata.outputFile;
 const cert=cp.execFileSync(path.join(process.env.ANDROID_HOME,'build-tools/35.0.0/apksigner'),['verify','--print-certs',apk],{encoding:'utf8'});
 console.log(cert);
 if(!cert.includes('ef73f2a3a7c10d308a73caee4423c2c1a3318ddc0775ba6a69a35d68ddb00b0a'))throw Error('Signing identity changed; refusing to release');
 fs.mkdirSync('release',{recursive:true});fs.copyFileSync(apk,'release/app.apk');
 cp.execFileSync('git',['archive','--format=zip','--output=release/source.zip','HEAD']);
 const bytes=fs.readFileSync('release/app.apk');const manifest={versionCode:code,versionName:version,apkUrl:'https://github.com/'+repo+'/releases/download/'+tag+'/app.apk',sha256:crypto.createHash('sha256').update(bytes).digest('hex'),size:bytes.length};
 fs.writeFileSync('release/update.json',JSON.stringify(manifest,null,2));
 const release=await api('/repos/'+repo+'/releases','POST',{tag_name:tag,target_commitish:process.env.GITHUB_SHA,name:version,draft:true,prerelease:false,body:'Built and signed in GitHub Actions from '+process.env.GITHUB_SHA+'. Install over the existing app; local data remains on the phone. Corresponding source and licenses included.'});
 for(const [name,type] of [['app.apk','application/vnd.android.package-archive'],['source.zip','application/zip'],['update.json','application/json']]){const r=await fetch(release.upload_url.split('{')[0]+'?name='+name,{method:'POST',headers:{Authorization:'Bearer '+token,'User-Agent':'money-cloud-release','Content-Type':type},body:fs.readFileSync('release/'+name)});if(!r.ok)throw Error('Asset upload failed '+name+' HTTP '+r.status);}
 const published=await api('/repos/'+repo+'/releases/'+release.id,'PATCH',{draft:false,make_latest:'true'});console.log(published.html_url);
})().catch(e=>{console.error(e.message);process.exitCode=1;});
