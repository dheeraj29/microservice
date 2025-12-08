import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, RouterStateSnapshot } from '@angular/router';
import { environment } from '../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class LoginhandlerService {

  async canActivate(next: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
    if(localStorage.getItem("__access__")) {
      return true;
    } else {
      const gen_challenge = this.generatePKCEPair();
      gen_challenge.then((code_challenge) => {
        window.location.href=environment.AUTH_URL+"/oauth2/authorize?response_type=code&client_id="+environment.CLIENT_ID+"&redirect_uri="+encodeURIComponent(environment.REDIRECT_URL)+"&code_challenge="+code_challenge+"&code_challenge_method=S256&state="+state.url;
      });
      return false;
    }
  }

  private async generatePKCEPair() {
    const NUM_OF_BYTES = Math.floor(Math.random() * (128 - 43 + 1) + 43); // Total of 44 characters (1 Bytes = 2 char) (standard states that: 43 chars <= verifier <= 128 chars)
    //let array = new Uint8Array(NUM_OF_BYTES/2);
    //window.crypto.getRandomValues(array);
    //const challenge_verify = Array.from(array, this.dec2hex).join('');
    const characters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~';
    let counter = Math.floor(NUM_OF_BYTES/2) - 1;
    let challenge_verify = "";
    while (counter >= 0) {
      challenge_verify += characters.charAt(Math.floor(Math.random() * characters.length));
      counter -= 1;
    }
    
    const code_hash = await this.sha256(challenge_verify);
    const base64Encoded = this.base64urlencode(code_hash);
    document.cookie = "challenge="+challenge_verify+"; Path=/callback"+"; SameSite=Strict";
    return base64Encoded;
  }

  private dec2hex(dec: number) {
    return dec.toString(16);
  }

  private sha256(plain: string) { // returns promise ArrayBuffer
    const encoder = new TextEncoder();
    const data = encoder.encode(plain);
    return window.crypto.subtle.digest('SHA-256', data);
  }

  private base64urlencode(hash: ArrayBuffer) {
    // (replace + with -, replace / with _, trim trailing =)
    return btoa(String.fromCharCode(...new Uint8Array(hash)))
      .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }

}

export const AuthGuard: CanActivateFn = (next: ActivatedRouteSnapshot, state: RouterStateSnapshot): Promise<boolean> => {
  return inject(LoginhandlerService).canActivate(next, state);
}