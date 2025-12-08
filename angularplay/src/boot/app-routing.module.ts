import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '../services/loginhandler.service';
import { SelectpageComponent } from './selectpage/selectpage.component';

const routes: Routes = [
  {
    path: 'admin',
    loadChildren: () =>
      import(`../modules/admin/admin.module`).then((m) => m.AdminModule), canActivate: [AuthGuard]
  },
  {
    path: 'booking',
    loadChildren: () =>
      import(`../modules/booking/booking.module`).then((m) => m.BookingModule),
  },
  {
    path: 'callback',
    component: SelectpageComponent
  },
  {
    path: 'home',
    component: SelectpageComponent
  },
  {
    path: '',
    component: SelectpageComponent
  }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)
  ],
  exports: [RouterModule
  ]
})
export class AppRoutingModule { }
