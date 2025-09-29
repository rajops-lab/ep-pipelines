kb=kafka1:9095
alias kcongrp="kafka-consumer-groups.sh --bootstrap-server $kb "
alias kcat="kcat -b $kb "

kwrite() {
    topic=$1
    msg=$2
    echo $2 | kcat -t $topic -P
}

kread() {
    topic=$1
    kcat -t $topic -C
}


kdesc() {
  group=$1
  if [ -z "$group" ]
  then
        kcongrp --all-groups --describe
  else
        kcongrp --group $group --describe
  fi
}
