#!/bin/sh

if ls  ${WORKSPACE}/exports/ecom-dw/sites/replacements* 1> /dev/null 2>&1; then
    echo "files do exist"
    
   if [ ! -z "$Export_Site_ID" ]
   then
      echo "NPM Installation is in progress... "

    npm install

echo "Export site....."
if [ "$Site_Export" = "without_Catalog" ]
then
    grunt exportUnits --build.project.name=adidas --webdav.server=${Export_Instance}  --webdav.username=${Export_Instance_Username} --webdav.password=${Export_Instance_Password} --export-name=site_template --exportUnits.sites=${Export_Site_ID}
elif [ "$Site_Export" = "with_Catalog" ]
then
    grunt exportUnits_withCatalog --build.project.name=adidas --webdav.server=${Export_Instance}  --webdav.username=${Export_Instance_Username} --webdav.password=${Export_Instance_Password} --export-name=site_template --exportUnits.sites=${Export_Site_ID}
else
   exit 1
fi
echo "create a New site template from export....."
find "${WORKSPACE}/output/" -name "*.zip" -type f -delete
npm -s run create_site_template -- -i --instance=${Import_Instance}
echo "Importing New site template....."
find "${WORKSPACE}/output/" -type f -iname "*.zip" -exec basename {} .zip \;  > outfile.txt
file="$(cat ${WORKSPACE}/outfile.txt)"
grunt adi_newsitetemplateImport -build.project.name=adidas -build.project.version=0   -webdav.username=${Import_Instance_Username}  -webdav.password=${Import_Instance_Password} -webdav.server=${Import_Instance} -jen_workspace=${WORKSPACE} --export_filename=${file}
    else
      echo " Site id not found"
      exit 1
    fi      
else
    echo "files do not exist"
fi