UPDATE "template" SET jsonb= '{
 "id": "263d4e33-db8d-4e07-9060-11f442320c05",
 "description": "Account activation email",
 "outputFormats": [
   "text/html"
 ],
 "templateResolver": "mustache",
 "localizedTemplates": {
   "en": {
     "header": "Complete activation of your Folio account",
     "body": "<p>{{user.personal.firstName}}</p><p>Your FOLIO account has been activated.</p><p>Your username is {{user.username}}.</p><p>To complete activation of your account, please use the following link to create a password for your FOLIO account: <a href={{link}}>visit this link</a></p><p>This link is only active for a short time. If the link has already expired, please <a href={{forgotPasswordLink}}>visit this link</a> to get a new, active link.</p><p>Regards,</p><p>{{institution.name}} FOLIO Administration</p>"
   }
 }
}'
WHERE id = '263d4e33-db8d-4e07-9060-11f442320c05';
