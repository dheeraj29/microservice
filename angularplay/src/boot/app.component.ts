import { HttpClient } from '@angular/common/http';
import { Component, inject } from '@angular/core';

@Component({
    selector: 'app-root',
    templateUrl: './app.component.html',
    styleUrl: './app.component.scss',
    standalone: false
})
export class AppComponent {
  title = 'angularplay';
  private httpClient = inject(HttpClient);

  public logout() {
    let signoff = confirm("Do you want to logout?");
    if(signoff && localStorage["__access__"]) {
      const verifier = (cookiename: string) => {
        return document.cookie.match('(^|;)\\s*' + cookiename + '\\s*=\\s*([^;]+)')?.pop() || '';
      };
      let retoken = verifier("refresh");
      this.httpClient.post<any>("/generateToken/v1/revokeToken?token="+retoken, null).subscribe({
        next: data => {
          localStorage.removeItem("__access__");
          document.cookie = "refresh=; SameSite=Strict; Secure; HttpOnly; Max-Age=0";
        },
        error: error => {
          console.log(error);
        }
      });
    } else {
      console.log("logout false");
    }
  }
}