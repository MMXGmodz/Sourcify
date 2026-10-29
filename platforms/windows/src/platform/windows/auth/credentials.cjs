'use strict';
const fs=require('node:fs'),path=require('node:path');
class CredentialStore {
  constructor(file,safeStorage){this.file=file;this.storage=safeStorage;}
  available(){return this.storage.isEncryptionAvailable()&&(!this.storage.getSelectedStorageBackend||this.storage.getSelectedStorageBackend()!=='basic_text');}
  save(value){
    if(!this.available())throw new Error('Sicherer Credential Store ist nicht verfügbar. Anmeldung wird nicht unsicher gespeichert.');
    fs.mkdirSync(path.dirname(this.file),{recursive:true});const temp=this.file+'.tmp';
    fs.writeFileSync(temp,this.storage.encryptString(JSON.stringify(value)),{mode:0o600});fs.renameSync(temp,this.file);
  }
  load(){if(!fs.existsSync(this.file))return null;if(!this.available())throw new Error('Sicherer Credential Store ist nicht verfügbar.');return JSON.parse(this.storage.decryptString(fs.readFileSync(this.file)));}
  clear(){for(const file of [this.file,this.file+'.tmp'])fs.rmSync(file,{force:true});}
}
module.exports={CredentialStore};
