#export SERVER=localhost:8080;
#export JSESSIONID=blah-blah;

echo curl "http://${SERVER}/MetadataServicesToolkit/solr/update;jsessionid=${JSESSIONID}" -H "Content-Type: text/xml" --data-binary '<delete><query>*:*</query></delete>'
curl "http://${SERVER}/MetadataServicesToolkit/solr/update;jsessionid=${JSESSIONID}" -H "Content-Type: text/xml" --data-binary '<delete><query>*:*</query></delete>'
echo ; echo 

echo curl "http://${SERVER}/MetadataServicesToolkit/solr/update;jsessionid=${JSESSIONID}" -H "Content-Type: text/xml" --data-binary '<delete><query>*:*</query></delete>'
curl "http://${SERVER}/MetadataServicesToolkit/solr/update;jsessionid=${JSESSIONID}" -H "Content-Type: text/xml" --data-binary '<commit />'
echo ; echo 

echo "http://${SERVER}/MetadataServicesToolkit/solr/update;jsessionid=${JSESSIONID}" -H "Content-Type: text/xml" --data-binary '<optimize />'
curl "http://${SERVER}/MetadataServicesToolkit/solr/update;jsessionid=${JSESSIONID}" -H "Content-Type: text/xml" --data-binary '<optimize />'
echo ; echo 

echo curl "http://${SERVER}/MetadataServicesToolkit/devAdmin;jsessionid=${JSESSIONID}?op=refreshSolr"
curl "http://${SERVER}/MetadataServicesToolkit/devAdmin;jsessionid=${JSESSIONID}?op=refreshSolr"
