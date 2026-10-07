import test from 'node:test';
import assert from 'node:assert/strict';
import { hasConfirmedHospitalLocation } from './confirmedHospitalLocation.js';

test('only approved and explicitly confirmed hospital coordinates are mappable', () => {
  assert.equal(hasConfirmedHospitalLocation({ locationConfirmed: true, verificationStatus: 'APPROVED', latitude: 12.9, longitude: 77.6 }), true);
  assert.equal(hasConfirmedHospitalLocation({ locationConfirmed: false, verificationStatus: 'APPROVED', latitude: 12.9, longitude: 77.6 }), false);
  assert.equal(hasConfirmedHospitalLocation({ locationConfirmed: true, verificationStatus: 'PENDING', latitude: 12.9, longitude: 77.6 }), false);
  assert.equal(hasConfirmedHospitalLocation({ locationConfirmed: true, verificationStatus: 'APPROVED', latitude: null, longitude: 77.6 }), false);
  assert.equal(hasConfirmedHospitalLocation({ locationConfirmed: true, verificationStatus: 'APPROVED', latitude: '', longitude: 77.6 }), false);
});
