package cz.polankam.dss.validator;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.zaxxer.hikari.HikariDataSource;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.service.crl.JdbcCacheCRLSource;
import eu.europa.esig.dss.service.crl.OnlineCRLSource;
import eu.europa.esig.dss.service.http.commons.CommonsDataLoader;
import eu.europa.esig.dss.service.http.commons.FileCacheDataLoader;
import eu.europa.esig.dss.service.http.commons.OCSPDataLoader;
import eu.europa.esig.dss.service.ocsp.JdbcCacheOCSPSource;
import eu.europa.esig.dss.service.ocsp.OnlineOCSPSource;
import eu.europa.esig.dss.simplereport.SimpleReport;
import eu.europa.esig.dss.spi.client.http.DSSFileLoader;
import eu.europa.esig.dss.spi.client.http.DataLoader;
import eu.europa.esig.dss.spi.client.http.IgnoreDataLoader;
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource;
import eu.europa.esig.dss.spi.x509.CertificateSource;
import eu.europa.esig.dss.spi.x509.KeyStoreCertificateSource;
import eu.europa.esig.dss.tsl.function.OfficialJournalSchemeInformationURI;
import eu.europa.esig.dss.tsl.job.TLValidationJob;
import eu.europa.esig.dss.tsl.source.LOTLSource;
import eu.europa.esig.dss.validation.CertificateVerifier;
import eu.europa.esig.dss.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.executor.ValidationLevel;
import eu.europa.esig.dss.validation.reports.Reports;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Locale;

import static org.slf4j.Logger.ROOT_LOGGER_NAME;

/**
 * Created by Martin Polanka on 11.03.2020.
 */
public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Wrong number of arguments. Arguments: SIGNED_FILE");
            return;
        }

        try (PrintWriter results = new PrintWriter("results-tmp.log", "UTF-8")) {
            validate(args[0], results);
        }
    }

    private static Reports validate(String signedFile, PrintWriter results) throws IOException {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.WARN);

//        Logger dssLogger = (Logger) LoggerFactory.getLogger("eu.europa.esig.dss");
//        dssLogger.setLevel(Level.DEBUG);

        byte[] signedBytes = Files.readAllBytes(Paths.get(signedFile));
        DSSDocument signature = new InMemoryDocument(signedBytes, "sample.signed.pdf");
        SignedDocumentValidator validator = SignedDocumentValidator.fromDocument(signature);
        validator.setCertificateVerifier(buildCertificateVerifier());
        validator.setLocale(Locale.ENGLISH);
        validator.setValidationLevel(ValidationLevel.ARCHIVAL_DATA);

        System.out.println("###################################################################");
        Reports reports = validator.validateDocument();
        SimpleReport simpleReport = reports.getSimpleReport();
        // Note: assuming file signed with only one signature
        String id = simpleReport.getFirstSignatureId();

//        String homeDir = System.getProperty("user.home");
//        Files.write(Paths.get(homeDir, "simple-report.xml"), reports.getXmlSimpleReport().getBytes());
//        Files.write(Paths.get(homeDir, "detailed-report.xml"), reports.getXmlDetailedReport().getBytes());

        System.out.println("Indication: " + simpleReport.getIndication(id));
        System.out.println("Subindication: " + simpleReport.getSubIndication(id));
//        System.out.println("Validation:");
//        System.out.println("  Info    : " + simpleReport.getInfo(id));
//        System.out.println("  Errors  : " + simpleReport.getErrors(id));
//        System.out.println("  Warnings: " + simpleReport.getWarnings(id));

        // write results to file
        results.println("Indication: " + simpleReport.getIndication(id));
        results.println("Subindication: " + simpleReport.getSubIndication(id));
        results.println("------------------------------------------------------------");

        return reports;
    }

    private static CertificateVerifier buildCertificateVerifier() {
        CommonsDataLoader commonsDataLoader = dataLoader();
        OCSPDataLoader ocspDataLoader = ocspDataLoader();
        DataSource dataSource = dataSource();
        //FileCacheDataLoader fileCacheDataLoader = fileCacheDataLoader(commonsDataLoader);
        OnlineCRLSource onlineCRLSource = onlineCRLSource(commonsDataLoader);
        OnlineOCSPSource onlineOCSPSource = onlineOcspSource(ocspDataLoader);
        JdbcCacheCRLSource jdbcCacheCRLSource = cachedCRLSource(dataSource, onlineCRLSource);
        JdbcCacheOCSPSource jdbcCacheOCSPSource = cachedOCSPSource(dataSource, onlineOCSPSource);
        TrustedListsCertificateSource trustedListsCertificateSource = trustedListSource();
        KeyStoreCertificateSource keyStoreCertificateSource = ojContentKeyStore();
        File tlCacheDirectory = tlCacheDirectory();
        DSSFileLoader onlineLoader = onlineLoader(commonsDataLoader, tlCacheDirectory);
        DSSFileLoader offlineLoader = offlineLoader(tlCacheDirectory);
        LOTLSource lotlSource = europeanLOTL(keyStoreCertificateSource);
        TLValidationJob job = job(trustedListsCertificateSource, lotlSource, offlineLoader, onlineLoader);
        return certificateVerifier(jdbcCacheCRLSource, jdbcCacheOCSPSource, commonsDataLoader, trustedListsCertificateSource);
    }

    ////////////////////////////////////////////////////////////////////////////

    private static DataSource dataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("DSS-Hikari-Pool");
        ds.setJdbcUrl("jdbc:h2:mem:testdb");
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setAutoCommit(false);
        return ds;
    }

    private static JdbcCacheCRLSource cachedCRLSource(DataSource dataSource, OnlineCRLSource onlineCrlSource) {
        JdbcCacheCRLSource jdbcCacheCRLSource = new JdbcCacheCRLSource();
        jdbcCacheCRLSource.setDataSource(dataSource);
        jdbcCacheCRLSource.setProxySource(onlineCrlSource);
        jdbcCacheCRLSource.setDefaultNextUpdateDelay((long) (60 * 3)); // 3 minutes
        try {
            jdbcCacheCRLSource.initTable();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return jdbcCacheCRLSource;
    }

    private static JdbcCacheOCSPSource cachedOCSPSource(DataSource dataSource, OnlineOCSPSource onlineOcspSource) {
        JdbcCacheOCSPSource jdbcCacheOCSPSource = new JdbcCacheOCSPSource();
        jdbcCacheOCSPSource.setDataSource(dataSource);
        jdbcCacheOCSPSource.setProxySource(onlineOcspSource);
        jdbcCacheOCSPSource.setDefaultNextUpdateDelay((long) (60 * 3)); // 3 minutes
        try {
            jdbcCacheOCSPSource.initTable();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return jdbcCacheOCSPSource;
    }

    private static CommonsDataLoader dataLoader() {
        return new CommonsDataLoader();
    }

    private static OCSPDataLoader ocspDataLoader() {
        return new OCSPDataLoader();
    }

    private static FileCacheDataLoader fileCacheDataLoader(DataLoader dataLoader) {
        FileCacheDataLoader fileCacheDataLoader = new FileCacheDataLoader();
        fileCacheDataLoader.setDataLoader(dataLoader);
        return fileCacheDataLoader;
    }

    private static OnlineCRLSource onlineCRLSource(DataLoader dataLoader) {
        OnlineCRLSource onlineCRLSource = new OnlineCRLSource();
        onlineCRLSource.setDataLoader(dataLoader);
        return onlineCRLSource;
    }

    private static OnlineOCSPSource onlineOcspSource(OCSPDataLoader ocspDataLoader) {
        OnlineOCSPSource onlineOCSPSource = new OnlineOCSPSource();
        onlineOCSPSource.setDataLoader(ocspDataLoader);
        return onlineOCSPSource;
    }

    private static TrustedListsCertificateSource trustedListSource() {
        return new TrustedListsCertificateSource();
    }

    private static CertificateVerifier certificateVerifier(JdbcCacheCRLSource crlSource,
                                                           JdbcCacheOCSPSource ocspSource,
                                                           DataLoader dataLoader,
                                                           TrustedListsCertificateSource trustedListSource) {
        CommonCertificateVerifier certificateVerifier = new CommonCertificateVerifier();
        certificateVerifier.setCrlSource(crlSource);
        certificateVerifier.setOcspSource(ocspSource);
        certificateVerifier.setDataLoader(dataLoader);
        certificateVerifier.setTrustedCertSources(trustedListSource);

        // Default configs
        certificateVerifier.setExceptionOnMissingRevocationData(true);
        certificateVerifier.setCheckRevocationForUntrustedChains(false);

        return certificateVerifier;
    }

    private static KeyStoreCertificateSource ojContentKeyStore() {
        try {
            return new KeyStoreCertificateSource("data/dss-keystore.p12",
                    "PKCS12",
                    "dss-password");
        } catch (IOException e) {
            throw new DSSException("Unable to load the file ", e);
        }
    }

    private static TLValidationJob job(TrustedListsCertificateSource trustedListsCertificateSource,
                                       LOTLSource lotlSource,
                                       DSSFileLoader offlineLoader,
                                       DSSFileLoader onlineLoader) {
        TLValidationJob job = new TLValidationJob();
        job.setTrustedListCertificateSource(trustedListsCertificateSource);
        job.setListOfTrustedListSources(lotlSource);
        job.setOfflineDataLoader(offlineLoader);
        job.setOnlineDataLoader(onlineLoader);
        job.onlineRefresh();
        return job;
    }

    private static DSSFileLoader onlineLoader(DataLoader dataLoader, File tlCacheDirectory) {
        FileCacheDataLoader offlineFileLoader = new FileCacheDataLoader();
        offlineFileLoader.setCacheExpirationTime(0);
        offlineFileLoader.setDataLoader(dataLoader);
        offlineFileLoader.setFileCacheDirectory(tlCacheDirectory);
        return offlineFileLoader;
    }

    private static LOTLSource europeanLOTL(CertificateSource ojContentKeyStore) {
        LOTLSource lotlSource = new LOTLSource();
        lotlSource.setUrl("https://ec.europa.eu/tools/lotl/eu-lotl.xml");
        lotlSource.setCertificateSource(ojContentKeyStore);
        lotlSource.setSigningCertificatesAnnouncementPredicate(new OfficialJournalSchemeInformationURI(
                "https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=uriserv:OJ.C_.2019.276.01.0001.01.ENG"));
        lotlSource.setPivotSupport(true);
        return lotlSource;
    }

    private static DSSFileLoader offlineLoader(File tlCacheDirectory) {
        FileCacheDataLoader offlineFileLoader = new FileCacheDataLoader();
        offlineFileLoader.setCacheExpirationTime(Long.MAX_VALUE);
        offlineFileLoader.setDataLoader(new IgnoreDataLoader());
        offlineFileLoader.setFileCacheDirectory(tlCacheDirectory);
        return offlineFileLoader;
    }

    private static File tlCacheDirectory() {
        File rootFolder = new File(System.getProperty("java.io.tmpdir"));
        File tslCache = new File(rootFolder, "dss-tsl-loader");
        if (tslCache.mkdirs()) {
            System.out.println("TL Cache folder : " + tslCache.getAbsolutePath());
        }
        return tslCache;
    }
}

