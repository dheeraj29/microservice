import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { AdminRoutingModule } from './admin-routing.module';
import { ConsoleComponent } from './console/console.component';
import { OrdersComponent } from './orders/orders.component';


@NgModule({
  declarations: [
    ConsoleComponent,
    OrdersComponent
  ],
  imports: [
    CommonModule,
    AdminRoutingModule
  ]
})
export class AdminModule { }
