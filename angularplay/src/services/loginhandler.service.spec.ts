import { TestBed } from '@angular/core/testing';

import { LoginhandlerService } from './loginhandler.service';

describe('LoginhandlerService', () => {
  let service: LoginhandlerService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(LoginhandlerService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
