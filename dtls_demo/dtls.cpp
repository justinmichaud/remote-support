// Reference: https://gist.github.com/roxlu/9835067
// https://sourceforge.net/p/campagnol/code/HEAD/tree/trunk/
#include <iostream>
#include <cstdlib>
#include <stdexcept>
#include <stdio.h>
#include <stdlib.h>

#include <openssl/err.h>
#include <openssl/dh.h>
#include <openssl/ssl.h>
#include <openssl/conf.h>
#include <openssl/engine.h>

class DTLSConnection {
public:
    SSL_CTX* ctx; // SSL Context
    SSL* ssl; // SSL Connection - SSL_read/write to read/write unencrypted data, 
    BIO* in_bio; // Encrypted data
    BIO* out_bio;
    bool server;
    
    DTLSConnection(bool server, std::string cert, std::string private_key) : server{server} {
        try {
            this->init_ssl(cert, private_key);
        } catch (std::runtime_error e) {
            this->~DTLSConnection();
            throw e;
        }
    }
    
    ~DTLSConnection() {
        if(this->ctx) { 
            SSL_CTX_free(this->ctx);
            this->ctx = NULL;
        }

        if(this->ssl) {
            SSL_free(this->ssl);
            this->ssl = NULL;
        }
    }
    
    /*
     * Takes filename of cert and private key files
     */
    void init_ssl(std::string cert, std::string private_key) {
        // create a new context using DTLS
        this->ctx = SSL_CTX_new(DTLSv1_method());
        if(!this->ctx) {
            ERR_print_errors_fp(stderr);
            throw std::runtime_error("Error: cannot create SSL_CTX.");
        }
        
        // Set the supported cipher list
        if (SSL_CTX_set_cipher_list(this->ctx, "ALL:!ADH:!LOW:!EXP:!MD5:@STRENGTH") != 1) {
            ERR_print_errors_fp(stderr);
            throw std::runtime_error("Error: cannot set the cipher list.");
        }
        
        // Force the client to send its certificate
        SSL_CTX_set_verify(this->ctx, SSL_VERIFY_PEER|SSL_VERIFY_FAIL_IF_NO_PEER_CERT, NULL);
        
        // Enable srtp
        if (SSL_CTX_set_tlsext_use_srtp(this->ctx, "SRTP_AES128_CM_SHA1_80") != 0) {
            ERR_print_errors_fp(stderr);
            throw std::runtime_error("Error: cannot enable srtp");
        }
        
        // Load keys
        if (SSL_CTX_use_certificate_file(this->ctx, cert.c_str(), SSL_FILETYPE_PEM) != 1) {
            ERR_print_errors_fp(stderr);
            throw std::runtime_error("Error: cannot load cert file " + cert);
        }
        
        if (SSL_CTX_use_PrivateKey_file(this->ctx, private_key.c_str(), SSL_FILETYPE_PEM) != 1) {
            ERR_print_errors_fp(stderr);
            throw std::runtime_error("Error: cannot load key file " + private_key);
        }
        
        // Verify private key
        if (SSL_CTX_check_private_key(this->ctx) != 1) {
            ERR_print_errors_fp(stderr);
            throw std::runtime_error("Error: checking private key failed " + private_key);
        }
        
        // Create ssl object
        this->ssl = SSL_new(this->ctx);
        if(!this->ssl) {
            ERR_print_errors_fp(stderr);
            throw std::runtime_error("Error: could not create ssl");
        }
        
        // Set callback
        SSL_set_info_callback(this->ssl, DTLSConnection::info_callback_ssl);
        
        /* bios */
        this->in_bio = BIO_new(BIO_s_mem());
        if(this->in_bio == NULL) {
            throw std::runtime_error("Error: could not allocate input bio");
        }
        
        BIO_set_mem_eof_return(this->in_bio, -1); /* see: https://www.openssl.org/docs/crypto/BIO_s_mem.html */
        
        this->out_bio = BIO_new(BIO_s_mem());
        if(this->out_bio == NULL) {
            throw std::runtime_error("Error: could not allocate output bio");
        }
        
        BIO_set_mem_eof_return(this->out_bio, -1); /* see: https://www.openssl.org/docs/crypto/BIO_s_mem.html */
        
        SSL_set_bio(this->ssl, this->in_bio, this->out_bio);
        
        if (this->server) SSL_set_accept_state(this->ssl);
        else SSL_set_connect_state(this->ssl);
    }
    
    void begin_ssl_handshake() {
        if (this->server) return;
        SSL_do_handshake(this->ssl);
    }
    
    int write_plaintext(char buf[]) {
        return SSL_write(this->ssl, buf, sizeof(buf));
    }
    
    int read_plaintext(char buf[]) {
        return SSL_read(this->ssl, buf, sizeof(buf));
    }
    
    int input_encrypted(char buf[]) {
        return BIO_write(this->in_bio, buf, sizeof(buf));
    }
    
    int output_encrypted(char buf[]) {
        return BIO_read(this->out_bio, buf, sizeof(buf));
    }
    
    static void info_callback_ssl(const SSL* ssl, int where, int ret) {
        if(ret == 0) {
            std::cout << "Error: SSL Error Occured." << std::endl;
            return;
        }

        if (where & SSL_CB_LOOP) std::cout << "LOOP" << std::endl;
        if (where & SSL_CB_HANDSHAKE_START) std::cout << "HANDSHAKE_START" << std::endl;
        if (where & SSL_CB_HANDSHAKE_DONE) std::cout << "HANDSHAKE_DONE" << std::endl;
    }
    
    static void init_openssl() {
        SSL_library_init();
        SSL_load_error_strings();
        ERR_load_BIO_strings();
        OpenSSL_add_all_algorithms();
    }

    static void end_openssl() {
        ERR_remove_state(0);
        ENGINE_cleanup();
        CONF_modules_unload(1);
        ERR_free_strings();
        EVP_cleanup();
        sk_SSL_COMP_free(SSL_COMP_get_compression_methods());
        CRYPTO_cleanup_all_ex_data();
    }
};

int server_main() {    
    DTLSConnection::init_openssl();    
    DTLSConnection* server = new DTLSConnection(true, "server_cert.pem", "server_key.pem");
    
    char buff[4096];
    
    while (true) {
        std::cout << "Not implemented" << std::endl;
    }
    
    delete server;
    DTLSConnection::end_openssl();    
    
    return 0;
}

int client_main() {    
    DTLSConnection::init_openssl();    
    DTLSConnection* client = new DTLSConnection(false, "client-cert.pem", "client-key.pem");
    
    char buf[4096];
    
    client->begin_ssl_handshake();
    client->write_plaintext("Hello World!");
    
    while (true) {
        int read = client->output_encrypted(buf);
        if (read > 0) std::cout << "Read " << read << ":" << buf << std::endl;
    }
    
    delete client;
    DTLSConnection::end_openssl();    
    
    return 0;
}

int main(int argc, char** argv) {
    if (argc != 2) {
        std::cout << "Incorrect number of arguments" << std::endl;
        return 1;
    }
    
    if (argv[1][0] == 's') {
        return server_main();
    }
    else {
        return client_main();
    }
}

