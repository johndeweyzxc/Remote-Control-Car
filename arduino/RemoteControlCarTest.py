import socket

host = "192.168.4.1"  # Replace with the IP address of your ESP32
port = 8080

message = "BREAK\n"

with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
    s.connect((host, port))
    s.sendall(message.encode())
    data = s.recv(1024)

print(f"Received from server: {data.decode()}")