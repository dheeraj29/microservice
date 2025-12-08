import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ConsoleComponent } from './console/console.component';
import { OrdersComponent } from './orders/orders.component';
import { AuthGuard } from '../../services/loginhandler.service';

const routes: Routes = [
  {path: '', component: ConsoleComponent},
  {path: 'orders', component: OrdersComponent, canActivate: [AuthGuard]}
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class AdminRoutingModule { }
