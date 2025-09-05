import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Omnibox } from './omnibox';

describe('Omnibox', () => {
  let component: Omnibox;
  let fixture: ComponentFixture<Omnibox>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Omnibox]
    })
    .compileComponents();

    fixture = TestBed.createComponent(Omnibox);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
