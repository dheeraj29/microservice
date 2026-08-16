import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { UserService } from '../../services/loginhandler.service';

@Component({
  selector: 'app-selectpage',
  templateUrl: './selectpage.component.html',
  styleUrl: './selectpage.component.scss',
  standalone: false
})
export class SelectpageComponent implements OnInit {
  userService = inject(UserService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  isAuthenticating = signal<boolean>(false);
  authErrorMessage = signal<string | null>(null);

  ngOnInit() {
    this.route.queryParamMap.subscribe(params => {
      const code = params.get('code');
      const error = params.get('error') || params.get('auth_error');

      if (error) {
        this.authErrorMessage.set(`Authentication failed: ${error}`);
      } else if (code) {
        this.router.navigate(['/callback'], { queryParams: { code } });
      } else {
        // If already logged in, redirect directly to role home
        if (this.userService.isAuthenticated()) {
          this.navigateToRoleHome();
        } else {
          this.userService.restoreSession().subscribe(isAuth => {
            if (isAuth) {
              this.navigateToRoleHome();
            }
          });
        }
      }
    });
  }

  private navigateToRoleHome() {
    if (this.userService.isAdmin()) {
      this.router.navigate(['/admin']);
    } else {
      this.router.navigate(['/booking']);
    }
  }

  login() {
    this.userService.loginWithKeycloak();
  }

  navigateTo(path: string) {
    if (!this.userService.isAuthenticated()) {
      this.userService.loginWithKeycloak();
    } else {
      this.router.navigate([path]);
    }
  }
}