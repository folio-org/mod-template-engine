INSERT INTO template (_id, jsonb) VALUES
('0ff6678f-53cd-4a32-9937-504c28f14077', '{
 "id": "0ff6678f-53cd-4a32-9937-504c28f14077",
 "description": "Template for password changed email",
 "outputFormats": [
   "txt",
   "html"
 ],
 "templateResolver": "mustache",
 "localizedTemplates": {
   "en": {
     "header": "Your Folio password changed",
     "body": "Dear {{user.firstName}},\n\nYour password has been changed.\nThis is a confirmation that your password was changed on {{dateTime}}.\n\nDid not change your password? Contact your Folio System Administrator to help secure your account.\n\nRegards,\n\nFolio Support"
   }
 }
}') ON CONFLICT DO NOTHING;
