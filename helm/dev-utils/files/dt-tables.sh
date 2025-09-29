#PGLOAD="psql -h localhost -p 25432 -U postgres assembly_cloud_ipms4"
#PGDUMP="pg_dump -h localhost -p 25432 -U postgres assembly_cloud_ipms4"
#PGLOAD="psql -h rds -p 5432 plant_master -U plant_master_rw"
#PGDUMP="pg_dump -h rds -p 5432 plant_master -U plant_master_ro"

# Local
#host=localhost
#port=25432
#cloud_username=postgres
#cloud_dbname=assembly_cloud_ipms4

# K8s
host=rds
port=5432
pm_username=plant_master_ro
pm_dbname=plant_master
cloud_username=assembly_cloud_rw
cloud_dbname=assembly_cloud

dt_get_dump_file() {
  echo dt-tables-`date +%Y-%m-%d`.sql
}

dt_dump_cmd() {
    if [ -z $1 ]; then
        echo "Need the input file."
        return 1
    fi
    f=$1
    pg_dump -h $host -p $port -U $pm_username $pm_dbname --format=p --column-inserts --rows-per-insert=10000 -t plant -t shop -t line -t station -t line_type -t shop_type -O -x --data-only --no-comments | egrep -v '^--' | egrep -v '^SET' | egrep -v '^SELECT' | sed -e 's/\bpublic./public.dt_/g' | sed -e 's/\r//g' > $f
}

dt_load_cmd() {
    f=$1
    if [ -z $f ]; then
        echo "Need the input file."
        return 1
    fi
    if [ ! -f $f ]; then
        echo "File $f does not exist."
        return 1
    fi
    tmp=`mktemp`
    echo 'BEGIN TRANSACTION;' > $tmp
    for t in dt_plant dt_shop dt_shop_type dt_line dt_line_type dt_station; do
        echo "delete from $t;" >> $tmp
    done
    cat $f >> $tmp
    echo 'END TRANSACTION;' >> $tmp
   cat $tmp | psql -h $host -p $port -U $cloud_username $cloud_dbname
}

dt_reload() {
    outfile=`dt_get_dump_file`
    dt_dump_cmd $outfile
    dt_load_cmd $outfile
}