import React, { useEffect, useState, useRef } from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom"; // EKLE
import App from "./App.jsx";
import keycloak from "./keycloak";

const Root = () => {
  const [isLogin, setLogin] = useState(false);
  const isRun = useRef(false);

  useEffect(() => {
    if (isRun.current) return;
    isRun.current = true;

    keycloak
      .init({
        onLoad: "login-required",
        redirectUri: window.location.origin
      })
      .then((res) => setLogin(res))
      .catch((err) => console.error(err));
  }, []);

  if (isLogin) {
    return (
      // BrowserRouter BURADA OLMALI
      <BrowserRouter>
        <App />
      </BrowserRouter>
    );
  }

  return <div>Yükleniyor...</div>;
};

ReactDOM.createRoot(document.getElementById("root")).render(<Root />);