TicketSystem (v0.0.2)

Güncelleme Notları:
Keycloak entegrasyonu tamamlandı. Testler için yapay zeka ile placeholder frontend tasarlandı. Testlerde rollerin sorunsuz tespit edilebildiği görüldü.


Ticket System Projesi Nedir:\
Şirketlerle ilerletilecek ticket sürecine ev sahipliği yapmak amacıyla geliştirilen, Spring Boot ve React tabanlı bir Full-Stack uygulamadır. Proje, ölçeklenebilirlik ve kolay dağıtım için tamamen Dockerize edilmiştir.

Kurulum:\
Sistemi bilgisayarınızda çalıştırmak için tek yapmanız gereken Docker Desktop'ın açık olduğundan emin olmak ve terminalde şu komutu çalıştırmaktır:

>docker-compose up --build 

Uygulama ayağa kalktığında aşağıdaki adreslerden erişebilirsiniz:

Frontend: http://localhost:5173\
Backend API: http://localhost:8081\
Keycloak: http://localhost:8080