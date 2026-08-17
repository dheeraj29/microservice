import { NgModule, provideExperimentalZonelessChangeDetection } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { SelectpageComponent } from './selectpage/selectpage.component';
import { CallbackComponent } from './callback/callback.component';
import { LoginComponent } from './login/login.component';
import { LogoutComponent } from './logout/logout.component';
import { loginInterceptor } from '../interceptor/login.interceptor';

@NgModule({
  declarations: [
    AppComponent,
    SelectpageComponent,
    CallbackComponent,
    LoginComponent,
    LogoutComponent
  ],
  imports: [
    BrowserModule,
    CommonModule,
    FormsModule,
    AppRoutingModule
  ],
  providers: [
    provideExperimentalZonelessChangeDetection(),
    provideAnimationsAsync(),
    provideHttpClient(withInterceptors([loginInterceptor]))
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
