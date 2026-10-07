export function hasConfirmedHospitalLocation(hospital) {
  return hospital?.locationConfirmed === true
    && String(hospital?.verificationStatus || '').toUpperCase() === 'APPROVED'
    && String(hospital?.latitude ?? '').trim() !== ''
    && String(hospital?.longitude ?? '').trim() !== ''
    && Number.isFinite(Number(hospital?.latitude))
    && Number.isFinite(Number(hospital?.longitude))
    && hospital.latitude !== null && hospital.longitude !== null;
}
