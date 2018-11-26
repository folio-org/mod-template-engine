INSERT INTO template (_id, jsonb) VALUES
('0ff6678f-53cd-4a32-9937-504c28f14077', '{
 "id": "0ff6678f-53cd-4a32-9937-504c28f14077",
 "description": "Template for password changed email",
 "outputFormats": [
   "text/plain"
 ],
 "templateResolver": "mustache",
 "localizedTemplates": {
   "en": {
     "header": "Your Folio password changed",
     "body": "Dear {{user.firstName}},\n\nYour password has been changed.\nThis is a confirmation that your password was changed on {{dateTime}}.\n\nDid not change your password? Contact your Folio System Administrator to help secure your account.\n\t\nRegards,\n\nFolio Support"
   }
 }
}'),
('263d4e33-db8d-4e07-9060-11f442320c05', '{
 "id": "263d4e33-db8d-4e07-9060-11f442320c05",
 "description": "Account activation email",
 "outputFormats": [
   "text/plain"
 ],
 "templateResolver": "mustache",
 "localizedTemplates": {
   "en": {
     "header": "Activate your Folio account",
     "body": "{{user.personal.firstName}}\n\nYour Folio account has been activated. Your username is {{user.username}}. To complete activation of your account, please use the following link to create a password for your Folio account: {{link}}\n\nIf you do not create a password within 24 hours of the delivery of this email, then contact your Folio Administrator to receive a new create password link.\n\nRegards,\n\n{{institution.name}} Folio Administration"
   }
 }
}'),
('ed8c1c67-897b-4a23-a702-c36e280c6a93', '{
 "id": "ed8c1c67-897b-4a23-a702-c36e280c6a93",
 "description": "Rest password email",
 "outputFormats": [
   "text/plain"
 ],
 "templateResolver": "mustache",
 "localizedTemplates": {
   "en": {
     "header": "Reset your Folio account",
     "body": "{{user.personal.firstName}}\n\nYour Folio password has been reset. Please use this link to reset your password: {{link}}\n\nIf you do not reset your password within 24 hours of the delivery of this email, then contact your Folio Administrator to reset your password.\n\nRegards,\n\n{{institution.name}} Folio Administration"
   }
 }
}') ON CONFLICT DO NOTHING;
