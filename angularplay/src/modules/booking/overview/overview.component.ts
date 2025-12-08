import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';

@Component({
    selector: 'app-overview',
    templateUrl: './overview.component.html',
    styleUrl: './overview.component.scss',
    standalone: false
})
export class OverviewComponent {

  private router: Router;

  constructor(router: Router) {
    this.router = router;
  }

  backNavigation() {
    console.log("navigating back");
    this.router.navigate(['../']);
  }

}