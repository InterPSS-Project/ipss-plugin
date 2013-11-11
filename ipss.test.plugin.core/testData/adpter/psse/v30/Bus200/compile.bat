@ECHO OFF
FLECS32 "cc1.flx" -L NO -F PSS001.FOR -EXPAND -W 100 -S * "C:\Program Files\PTI\PSSE29\PSSLIB" -F77
DF /4L132 /4Ya /4Nd /nologo /fltconsistency /fpe:0 /traceback /fpscomp:logicals /c /4Nportlib /Fo"CONEC.OBJ" /D"DLLI" /MD PSS001.FOR /I"C:\Program Files\PTI\PSSE29\PSSLIB" 
DEL PSS001.FOR
@ECHO OFF
FLECS32 "ct1.flx" -L NO -F PSS001.FOR -EXPAND -W 100 -S * "C:\Program Files\PTI\PSSE29\PSSLIB" -F77
DF /4L132 /4Ya /4Nd /nologo /fltconsistency /fpe:0 /traceback /fpscomp:logicals /c /4Nportlib /Fo"CONET.OBJ" /D"DLLI" /MD PSS001.FOR /I"C:\Program Files\PTI\PSSE29\PSSLIB" 
DEL PSS001.FOR
@REM
IF "%1"=="" GOTO SKIP
@REM ----------------CUT HERE----------------
FLECS32 "%1" -L NO -F "%1.F" -EXPAND -W 100 -S * "C:\Program Files\PTI\PSSE29\PSSLIB" -F77
DF /4L132 /4Ya /4Nd /nologo /fltconsistency /fpe:0 /traceback /fpscomp:logicals /c /4Nportlib /Fo"%1.OBJ" /D"DLLI" /MD "%1.F" /I"C:\Program Files\PTI\PSSE29\PSSLIB" 
@REM ----------------CUT HERE----------------
:SKIP
@ECHO If no errors, execute "CLOAD4"
