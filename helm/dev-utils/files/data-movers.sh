CLOUD_HOST=${CLOUD_HOST:-http://assembly-cloud}
CONFIGURATOR_HOST=${CONFIGURATOR_HOST:-http://configurator}
PLANT_DHARWAD_HOST=${PLANT_DHARWAD_HOST:-http://assembly-plant.assembly-dharwad}
PLANT_PUNE_HOST=${PLANT_PUNE_HOST:-http://assembly-plant.assembly-pune}
PLANT_LUCKNOW_HOST=${PLANT_LUCKNOW_HOST:-http://assembly-plant.assembly-lucknow}
PLANT_JAMSHEDPUR_HOST=${PLANT_JAMSHEDPUR_HOST:-http://assembly-plant.assembly-jamshedpur}

reset_ca_data() {
	for t in ca_part_families ca_stations ca_line_station_parts ca_part_metadata ca_plants; do psql-assembly-cloud-rw -c "delete from $t;"; done
}

publish_plants() {
	plant=$1
	curl -vv -XPOST -H "content-type: application/json" "$CLOUD_HOST/publish/md/dt/$plant?type=dt/plants" --data-raw ''
}

publish_lines() {
	plant=$1
	curl -vv -XPOST -H "content-type: application/json" "$CLOUD_HOST/publish/md/dt/$plant?type=dt/lines" --data-raw ''
}

publish_stations() {
	plant=$1
	curl -vv -XPOST -H "content-type: application/json" "$CLOUD_HOST/publish/md/dt/$plant?type=dt/stations" --data-raw ''
}

publish_ca_part_families() {
	plant=$1
	line=$2
	curl --location --request POST "$CLOUD_HOST/ca/plants/$plant/lines/$line/part-families"
}

publish_ca_part_metadata() {
	plant=$1
	curl --location --request POST "$CLOUD_HOST/ca/plants/$plant/parts"
}

sap_cloud_enable() {
	curl -X POST "$CLOUD_HOST/features/SAP_INTEGRATION/enable"
}

sap_plant_dharwad_enable() {
	curl -X POST "$PLANT_DHARWAD_HOST/features/SAP_INTEGRATION/enable"
}
sap_plant_lucknow_enable() {
	curl -X POST "$PLANT_LUCKNOW_HOST/features/SAP_INTEGRATION/enable"
}

sap_plant_pune_enable() {
	curl -X POST "$PLANT_PUNE_HOST/features/SAP_INTEGRATION/enable"
}

sap_plant_jamshedpur_enable() {
	curl -X POST "$PLANT_JAMSHEDPUR_HOST/features/SAP_INTEGRATION/enable"
}

update_dharwad_line_counter() {
	line=$1
	old_counter=$2
	new_counter=$3
	curl -X POST -H "content-type: application/json" "$PLANT_DHARWAD_HOST/internal/dev/set-counter/$line" --data-raw "{\"prevNumericCounter\": $old_counter, \"newCounter\": \"$new_counter\"}"
}
update_lucknow_line_counter() {
	line=$1
	old_counter=$2
	new_counter=$3
	curl -X POST -H "content-type: application/json" "$PLANT_LUCKNOW_HOST/internal/dev/set-counter/$line" --data-raw "{\"prevNumericCounter\": $old_counter, \"newCounter\": \"$new_counter\"}"
}
sap_cloud_disable() {
	curl -X POST "$CLOUD_HOST/features/SAP_INTEGRATION/disable"
}

sap_plant_dharwad_disable() {
	curl -X POST "$PLANT_DHARWAD_HOST/features/SAP_INTEGRATION/disable"
}
sap_plant_lucknow_disable() {
	curl -X POST "$PLANT_LUCKNOW_HOST/features/SAP_INTEGRATION/disable"
}

sap_plant_pune_disable() {
	curl -X POST "$PLANT_PUNE_HOST/features/SAP_INTEGRATION/disable"
}

sap_plant_jamshedpur_disable() {
	curl -X POST "$PLANT_JAMSHEDPUR_HOST/features/SAP_INTEGRATION/disable"
}

fetch_alternate_parts() {
	plant=$1
	curl -X POST "$CLOUD_HOST/alternate-parts/$plant"
}
