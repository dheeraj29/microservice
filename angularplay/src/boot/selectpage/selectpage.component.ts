import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';

@Component({
    selector: 'app-selectpage',
    templateUrl: './selectpage.component.html',
    styleUrl: './selectpage.component.scss',
    standalone: false
})
export class SelectpageComponent implements OnInit {
  private activateroute = inject(ActivatedRoute);
  private httpClient = inject(HttpClient);
  private router = inject(Router);

  ngOnInit() {
    if (this.activateroute.snapshot.queryParamMap.has('code')) {
      let params = new URLSearchParams();
      var code = this.activateroute.snapshot.queryParamMap.get('code') || "";
      var url_path = this.activateroute.snapshot.queryParamMap.get('state') || "";
      params.append('code', code);
      const verifier = (cookiename: string) => {
        return document.cookie.match('(^|;)\\s*' + cookiename + '\\s*=\\s*([^;]+)')?.pop() || '';
      };
      params.append('verifier', verifier("challenge"));
      let headers = new HttpHeaders({
        'Content-type': 'application/x-www-form-urlencoded'
      });
      this.httpClient.post<any>("/generateToken/v1/accessToken", params, { headers }).subscribe({
        next: data => {
          this.saveToken(data, url_path);
        },
        error: error => {
          console.log(error);
        }
      });
    }
  }
  saveToken(token: any, url_path: string): void {
    localStorage.setItem("__access__", token.access_token);
    document.cookie = "challenge=; Path=/callback; samesite=Strict; max-age=0";
    document.cookie = "refresh=" + token.refresh_token + "; samesite=strict; secure";
    this.router.navigateByUrl(url_path);
  }
}