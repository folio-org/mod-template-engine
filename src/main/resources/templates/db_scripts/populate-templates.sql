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
}'),
('ce9e3e2c-669a-4491-a12f-e0fdad066191', '{
 "id": "ce9e3e2c-669a-4491-a12f-e0fdad066191",
 "description": "Password is successfully created email template",
 "outputFormats": [
   "text/plain"
 ],
 "templateResolver": "mustache",
 "localizedTemplates": {
   "en": {
     "header": "Folio account successfully created",
     "body": "{{user.personal.firstName}}\n\nYour Folio account has been successfully created. You should now have access to Folio. \n\nShould you have any questions or you have received this email in error, please contact your Folio Administrator. \n\nRegards, \n\n{{institution.name}} Folio Administration"
   }
 }
}'),
('d0ee371a-f3f7-407a-b6f3-714362db6240', '{
 "id": "d0ee371a-f3f7-407a-b6f3-714362db6240",
 "description": "Username located email template",
 "outputFormats": [
   "text/plain"
 ],
 "templateResolver": "mustache",
 "localizedTemplates": {
   "en": {
     "header": "Folio account located",
     "body": "{{user.personal.firstName}}\n\nYour Folio username is {{user.username}}.\n\nPlease contact your Folio Administrator if you have any questions.\n\nRegards,\n\n{{institution.name}} Folio Administration"
   }
 }
}') ON CONFLICT DO NOTHING;
UPDATE "template" SET jsonb= '{
 "id": "0ff6678f-53cd-4a32-9937-504c28f14077",
 "description": "Template for password changed email",
 "outputFormats": [
   "text/plain"
 ],
 "templateResolver": "mustache",
 "localizedTemplates": {
   "en": {
     "header": "Your Folio password changed",
     "body": "Dear {{user.personal.firstName}},\n\nYour password has been changed.\nThis is a confirmation that your password was changed on {{dateTime}}.\n\nDid not change your password? Contact your Folio System Administrator to help secure your account.\n\t\nRegards,\n\nFolio Support"
   }
 }
}'
WHERE _id = '0ff6678f-53cd-4a32-9937-504c28f14077';