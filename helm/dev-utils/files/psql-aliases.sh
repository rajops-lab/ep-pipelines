for db in assembly_cloud configurator plant_master; do
	v=$(echo $db | tr _ -)
	alias psql-${v}="psql --host=rds --port=5432 --dbname=${db} --username=${db}_ro"
done

for db in assembly_cloud configurator; do
	v=$(echo $db | tr _ -)
	alias psql-${v}-rw="psql --host=rds --port=5432 --dbname=${db} --username=${db}_rw"
done

alias psql-assembly-pune="psql --host=rds-pune.assembly-pune --port=5432 --dbname=assembly_plant --username=assembly_plant_ro"
alias psql-assembly-pune-rw="psql --host=rds-pune.assembly-pune --port=5432 --dbname=assembly_plant --username=assembly_plant_rw"
alias psql-assembly-dharwad="psql --host=rds-dharwad.assembly-dharwad --port=5432 --dbname=assembly_plant --username=assembly_plant_ro"
alias psql-assembly-dharwad-rw="psql --host=rds-dharwad.assembly-dharwad --port=5432 --dbname=assembly_plant --username=assembly_plant_rw"
alias psql-assembly-lucknow="psql --host=rds-lucknow.assembly-lucknow --port=5432 --dbname=assembly_plant --username=assembly_plant_ro"
alias psql-assembly-lucknow-rw="psql --host=rds-lucknow.assembly-lucknow --port=5432 --dbname=assembly_plant --username=assembly_plant_rw"
alias psql-assembly-jamshedpur="psql --host=rds-jamshedpur.assembly-jamshedpur --port=5432 --dbname=assembly_plant --username=assembly_plant_ro"
alias psql-assembly-jamshedpur-rw="psql --host=rds-jamshedpur.assembly-jamshedpur --port=5432 --dbname=assembly_plant --username=assembly_plant_rw"
alias psql-bom-ro="psql --host=rds-avant-garde.avant-garde --port=5432 --dbname=bom --username=bom_ro"
alias psql-bom-rw="psql --host=rds-avant-garde.avant-garde --port=5432 --dbname=bom --username=bom_rw"
