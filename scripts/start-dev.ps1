Start-Process powershell -WindowStyle Hidden -ArgumentList '-NoExit','-Command','cd "C:\Users\23180\Desktop\新建文件夹\trackflow-platform"; mvn -pl server spring-boot:run'
Start-Process powershell -WindowStyle Hidden -ArgumentList '-NoExit','-Command','cd "C:\Users\23180\Desktop\新建文件夹\trackflow-platform"; mvn -pl mock-carrier spring-boot:run'
Start-Process powershell -WindowStyle Hidden -ArgumentList '-NoExit','-Command','cd "C:\Users\23180\Desktop\新建文件夹\trackflow-platform\web"; npm run dev'
