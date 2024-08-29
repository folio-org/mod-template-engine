UPDATE "template" SET jsonb= '{
 "id": "ed8c1c67-897b-4a23-a702-c36e280c6a93",
 "description": "Reset password email",
 "outputFormats": [
   "text/html"
 ],
 "templateResolver": "mustache",
 "localizedTemplates": {
   "en": {
     "header": "Reset your FOLIO account",
     "body": "<p>{{user.personal.firstName}}</p><p>Your request to reset your password has been received.</p> <p>To reset your password, please <a href={{link}}>visit this link</a>.</p><p>NOTE: This link is only active for a short time. If the link has already expired, please <a href={{forgotPasswordLink}}>visit this link</a> to get a new, active link.</p><p>Regards,</p><p>{{institution.name}} FOLIO Administration</p>"
   }
 }
}'
WHERE id = 'ed8c1c67-897b-4a23-a702-c36e280c6a93';
