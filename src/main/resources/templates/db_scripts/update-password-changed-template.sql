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
     "body": "Dear {{user.personal.firstName}},\n\nThis is a confirmation that your password was changed on {{detailedDateTime}}.\n\nDid not change your password? Please contact your FOLIO System Administrator to help secure your account.\n\t\nRegards,\n\nFOLIO Support"
   }
 }
}'
WHERE id = '0ff6678f-53cd-4a32-9937-504c28f14077';
