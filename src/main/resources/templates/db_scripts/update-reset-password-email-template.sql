UPDATE "template" SET jsonb= '{
 "id": "ed8c1c67-897b-4a23-a702-c36e280c6a93",
 "description": "Reset password email",
 "outputFormats": [
   "text/html"
 ],
 "templateResolver": "mustache",
 "localizedTemplates": {
   "en": {
     "header": "Reset your FOLIO account password",
     "body": "<p>{{user.personal.firstName}}</p><p>Your request to reset your password has been received.</p> <p><a href={{link}}>Reset your password</a>. This link is only valid for a short time. If it has already expired, <a href={{forgotPasswordLink}}>request a new link.</a></p><p>Regards,</p><p>{{institution.name}} FOLIO Administration</p>"
   }
 }
}'
WHERE id = 'ed8c1c67-897b-4a23-a702-c36e280c6a93';
