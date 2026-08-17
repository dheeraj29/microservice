import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard, AdminGuard } from '../services/loginhandler.service';
import { SelectpageComponent } from './selectpage/selectpage.component';
import { CallbackComponent } from './callback/callback.component';
import { LoginComponent } from './login/login.component';
import { LogoutComponent } from './logout/logout.component';

const routes: Routes = [
  {
    path: '',
    component: SelectpageComponent
  },
  {
    path: 'home',
    component: SelectpageComponent
  },
  {
    path: 'login',
    component: LoginComponent
  },
  {
    path: 'logout',
    component: LogoutComponent
  },
  {
    path: 'callback',
    component: CallbackComponent
  },
  {
    path: 'booking',
    loadChildren: () =>
      import('../modules/booking/booking.module').then((m) => m.BookingModule),
    canActivate: [AuthGuard]
  },
  {
    path: 'admin',
    loadChildren: () =>
      import('../modules/admin/admin.module').then((m) => m.AdminModule),
    canActivate: [AdminGuard]
  },
  {
    path: '**',
    redirectTo: ''
  }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
