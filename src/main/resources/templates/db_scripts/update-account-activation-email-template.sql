UPDATE "template" SET jsonb= '{
 "id": "263d4e33-db8d-4e07-9060-11f442320c05",
 "description": "Account activation email",
 "outputFormats": [
   "text/html"
 ],
 "templateResolver": "mustache",
 "localizedTemplates": {
   "en": {
     "header": "Activate your FOLIO account",
     "body": "<p>{{user.personal.firstName}}</p><p>Your FOLIO account has been activated.</p><p>Your username is {{user.username}}.</p><p><a href={{link}}>Set your password</a>to activate your account. This link is only valid for a short time. If it has already expired, <a href={{forgotPasswordLink}}>request a new link.</a></p><p>Regards,</p><p>{{institution.name}} FOLIO Administration</p>"
   }
 }
}'
WHERE id = '263d4e33-db8d-4e07-9060-11f442320c05';
