
   ;    test
   ;
   ;


CLS kaka

;\n
CLS

define adee EXIT

data:			; data to test
    DB $0F, $1F
    DB           $2F
    DB $$$ $3F
    DB	$4F
    DB	 $5F
    DB $6F, $k0, $0k
    DB $7F	; DB $8F
    DB  $9F
    DB $AF

JP main

CLS

main :
	CLS
asef

testusmaximus: wrongtest
DB $22

JP $FFFF

LD Rx, Ry
LD I, $FF00
SHR Rx, 1
SHL Rx, 1
DRW Rx, Ry
DRW Rx, Ry, $FF
JKNP Rx
JKP Rx
EXIT
