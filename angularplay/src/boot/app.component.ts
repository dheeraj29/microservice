import { Component, inject } from '@angular/core';
import { UserService } from '../services/loginhandler.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
  standalone: false
})
export class AppComponent {
  title = 'OmniBus';
  userService = inject(UserService);
  private router = inject(Router);

  login() {
    this.userService.loginWithKeycloak();
  }

  logout() {
    this.userService.logout();
  }

  navigateToHome() {
    if (this.userService.isAdmin()) {
      this.router.navigate(['/admin']);
    } else if (this.userService.isAuthenticated()) {
      this.router.navigate(['/booking']);
    } else {
      this.router.navigate(['/']);
    }
  }
}