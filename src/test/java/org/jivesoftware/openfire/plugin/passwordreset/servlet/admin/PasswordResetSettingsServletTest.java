package org.jivesoftware.openfire.plugin.passwordreset.servlet.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

import java.time.Duration;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import lombok.SneakyThrows;
import org.jivesoftware.admin.FlashMessageTag;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.plugin.passwordreset.Fixtures;
import org.jivesoftware.openfire.plugin.passwordreset.PasswordResetMailer;
import org.jivesoftware.openfire.plugin.passwordreset.PasswordResetPlugin;
import org.jivesoftware.openfire.plugin.passwordreset.PasswordResetTokenManager;
import org.jivesoftware.openfire.plugin.passwordreset.servlet.admin.PasswordResetSettingsServlet.Dto;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserProvider;
import org.jivesoftware.util.WebManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
class PasswordResetSettingsServletTest {

    private static final String REQUEST_URI = "/path/to/page";
    private PasswordResetSettingsServlet servlet;
    @Mock
    private HttpSession session;
    @Mock
    private HttpServletResponse response;
    @Mock
    private UserProvider userProvider;
    @Mock
    private RequestDispatcher requestDispatcher;
    @Mock
    private WebManager webManager;
    @Mock
    private PasswordResetMailer passwordResetMailer;
    @Mock
    private PasswordResetTokenManager tokenManager;
    @Mock
    private User user;

    @BeforeAll
    @SuppressWarnings("deprecation")
    static void beforeAll() {
        Fixtures.reconfigureOpenfireHome();
        XMPPServer.setInstance(Fixtures.mockXmppServer());
    }

    @BeforeEach
    void setUp() {
        Fixtures.clearExistingProperties();

        servlet = new PasswordResetSettingsServlet();
        PasswordResetSettingsServlet.initStatic(
            userProvider, () -> webManager, passwordResetMailer, tokenManager);
    }

    @SneakyThrows
    @Test
    void willForwardToJsp() {

        final HttpServletRequest request = blankRequest();

        servlet.doGet(request, response);

        verify(requestDispatcher).forward(request, response);

    }

    @SneakyThrows
    @Test
    void willSetValidForm() {

        final HttpServletRequest request = blankRequest();

        servlet.doGet(request, response);

        final Dto dto = getForwardedForm(request);
        assertThat(dto.isValid())
            .isEqualTo(true);
    }

    private HttpServletRequest blankRequest() {
        final HttpServletRequest request = mock(
            HttpServletRequest.class,
            withSettings().strictness(Strictness.LENIENT)
        );
        doReturn(REQUEST_URI).when(request)
            .getRequestURI();
        doReturn(requestDispatcher).when(request)
            .getRequestDispatcher("password-reset-settings.jsp");
        doReturn(session).when(request)
            .getSession();
        return request;
    }

    private Dto getForwardedForm(final HttpServletRequest request) {
        final ArgumentCaptor<Dto> argumentCaptor = ArgumentCaptor.forClass(Dto.class);
        verify(request).setAttribute(eq("dto"), argumentCaptor.capture());
        return argumentCaptor.getValue();
    }

    private HttpServletRequest requestWithValidFormSubmitted() {
        final HttpServletRequest request = blankRequest();
        doReturn("Update settings")
            .when(request)
            .getParameter("update");
        doReturn("true")
            .when(request)
            .getParameter("enabled");
        doReturn("https://example.com:7443/passwordreset")
            .when(request)
            .getParameter("server");
        doReturn("Openfire")
            .when(request)
            .getParameter("sender-name");
        doReturn("admin@example.org")
            .when(request)
            .getParameter("sender-address");
        doReturn("email subject")
            .when(request)
            .getParameter("subject");
        doReturn("email body")
            .when(request)
            .getParameter("body");
        doReturn("42")
            .when(request)
            .getParameter("expiryCount");
        doReturn("MINUTES")
            .when(request)
            .getParameter("expiryPeriod");
        doReturn("8")
            .when(request)
            .getParameter("minLength");
        doReturn("0")
            .when(request)
            .getParameter("maxLength");
        return request;
    }

    @SneakyThrows
    @Test
    void willValidateThatTheServerIsPresent() {

        final HttpServletRequest request = requestWithValidFormSubmitted();
        doReturn("").when(request).getParameter("server");

        servlet.doPost(request, response);

        final Dto dto = getForwardedForm(request);
        assertThat(dto.isValid())
            .isEqualTo(false);
        assertThat(dto.getServerError())
            .isEqualTo("???passwordreset.settings.no-server???");
    }

    @SneakyThrows
    @Test
    void willValidateThatTheServerIsNotTooLong() {

        final HttpServletRequest request = requestWithValidFormSubmitted();
        doReturn(new String(new char[4001]).replace('\0', 'X'))
            .when(request)
            .getParameter("server");

        servlet.doPost(request, response);

        final Dto dto = getForwardedForm(request);
        assertThat(dto.isValid())
            .isEqualTo(false);
        assertThat(dto.getServerError())
            .isEqualTo("???passwordreset.settings.server-too-long???");
    }

    @SneakyThrows
    @Test
    void willCheckTheServerIsAnHttpServer() {

        final HttpServletRequest request = requestWithValidFormSubmitted();
        doReturn("ftp://example.com")
            .when(request)
            .getParameter("server");

        servlet.doPost(request, response);

        final Dto dto = getForwardedForm(request);
        assertThat(dto.isValid())
            .isEqualTo(false);
        assertThat(dto.getServerError())
            .isEqualTo("???passwordreset.settings.server-no-http???");
    }

    @SneakyThrows
    @Test
    void willCheckTheSenderNameIsPresent() {
        final HttpServletRequest request = requestWithValidFormSubmitted();
        doReturn("")
            .when(request)
            .getParameter("sender-name");

        servlet.doPost(request, response);

        final Dto dto = getForwardedForm(request);
        assertThat(dto.isValid())
            .isEqualTo(false);
        assertThat(dto.getSenderNameError())
            .isEqualTo("???passwordreset.settings.no-sender-name???");
    }

    @SneakyThrows
    @Test
    void willValidateThatTheSenderNameIsNotTooLong() {

        final HttpServletRequest request = requestWithValidFormSubmitted();
        doReturn(new String(new char[4001]).replace('\0', 'X'))
            .when(request)
            .getParameter("sender-name");

        servlet.doPost(request, response);

        final Dto dto = getForwardedForm(request);
        assertThat(dto.isValid())
            .isEqualTo(false);
        assertThat(dto.getSenderNameError())
            .isEqualTo("???passwordreset.settings.sender-name-too-long???");
    }

    @SneakyThrows
    @Test
    void willCheckTheSenderAddressIsPresent() {
        final HttpServletRequest request = requestWithValidFormSubmitted();
        doReturn("")
            .when(request)
            .getParameter("sender-address");

        servlet.doPost(request, response);

        final Dto dto = getForwardedForm(request);
        assertThat(dto.isValid())
            .isEqualTo(false);
        assertThat(dto.getSenderAddressError())
            .isEqualTo("???passwordreset.settings.no-sender-address???");
    }

    @SneakyThrows
    @Test
    void willValidateThatTheSenderAddressIsNotTooLong() {

        final HttpServletRequest request = requestWithValidFormSubmitted();
        doReturn(new String(new char[4001]).replace('\0', 'X'))
            .when(request)
            .getParameter("sender-address");

        servlet.doPost(request, response);

        final Dto dto = getForwardedForm(request);
        assertThat(dto.isValid())
            .isEqualTo(false);
        assertThat(dto.getSenderAddressError())
            .isEqualTo("???passwordreset.settings.sender-address-too-long???");
    }

    @SneakyThrows
    @Test
    void willValidateThatTheSenderAddressIsAnAddress() {

        final HttpServletRequest request = requestWithValidFormSubmitted();
        doReturn("this is not an email address")
            .when(request)
            .getParameter("sender-address");

        servlet.doPost(request, response);

        final Dto dto = getForwardedForm(request);
        assertThat(dto.isValid())
            .isEqualTo(false);
        assertThat(dto.getSenderAddressError())
            .isEqualTo("???passwordreset.settings.sender-address-invalid???");
    }

    @SneakyThrows
    @Test
    void willValidateThatTheSubjectIsPresent() {

        final HttpServletRequest request = requestWithValidFormSubmitted();
        doReturn("").when(request).getParameter("subject");

        servlet.doPost(request, response);

        final Dto dto = getForwardedForm(request);
        assertThat(dto.isValid())
            .isEqualTo(false);
        assertThat(dto.getSubjectError())
            .isEqualTo("???passwordreset.settings.no-subject???");
    }

    @SneakyThrows
    @Test
    void willValidateThatTheSubjectIsNotTooLong() {

        final HttpServletRequest request = requestWithValidFormSubmitted();
        doReturn(new String(new char[4001]).replace('\0', 'X'))
            .when(request)
            .getParameter("subject");

        servlet.doPost(request, response);

        final Dto dto = getForwardedForm(request);
        assertThat(dto.isValid())
            .isEqualTo(false);
        assertThat(dto.getSubjectError())
            .isEqualTo("???passwordreset.settings.subject-too-long???");
    }

    @SneakyThrows
    @Test
    void willValidateThatTheBodyIsPresent() {

        final HttpServletRequest request = requestWithValidFormSubmitted();
        doReturn("").when(request).getParameter("body");

        servlet.doPost(request, response);

        final Dto dto = getForwardedForm(request);
        assertThat(dto.isValid())
            .isEqualTo(false);
        assertThat(dto.getBodyError())
            .isEqualTo("???passwordreset.settings.no-body???");
    }

    @SneakyThrows
    @Test
    void willValidateThatTheBodyIsNotTooLong() {

        final HttpServletRequest request = requestWithValidFormSubmitted();
        doReturn(new String(new char[4001]).replace('\0', 'X'))
            .when(request)
            .getParameter("body");

        servlet.doPost(request, response);

        final Dto dto = getForwardedForm(request);
        assertThat(dto.isValid())
            .isEqualTo(false);
        assertThat(dto.getBodyError())
            .isEqualTo("???passwordreset.settings.body-too-long???");
    }

    @SneakyThrows
    @Test
    void willValidateThatExpiryCountIsPresent() {

        final HttpServletRequest request = requestWithValidFormSubmitted();
        doReturn("")
            .when(request)
            .getParameter("expiryCount");

        servlet.doPost(request, response);

        final Dto dto = getForwardedForm(request);
        assertThat(dto.isValid())
            .isEqualTo(false);
        assertThat(dto.getExpiryError())
            .isEqualTo("???passwordreset.settings.expiry-count-integer???");
    }

    @SneakyThrows
    @Test
    void willValidateThatExpiryCountIsPositiveInteger() {

        final HttpServletRequest request = requestWithValidFormSubmitted();
        doReturn("0")
            .when(request)
            .getParameter("expiryCount");

        servlet.doPost(request, response);

        final Dto dto = getForwardedForm(request);
        assertThat(dto.isValid())
            .isEqualTo(false);
        assertThat(dto.getExpiryError())
            .isEqualTo("???passwordreset.settings.expiry-count-integer???");

    }

    @SneakyThrows
    @Test
    void willValidateThatExpiryPeriodIsPresent() {

        final HttpServletRequest request = requestWithValidFormSubmitted();
        doReturn("")
            .when(request)
            .getParameter("expiryPeriod");

        servlet.doPost(request, response);

        final Dto dto = getForwardedForm(request);
        assertThat(dto.isValid())
            .isEqualTo(false);
        assertThat(dto.getExpiryError())
            .isEqualTo("???passwordreset.settings.expiry-period-invalid???");
    }

    @SneakyThrows
    @Test
    void willValidateThatExpiryPeriodIsValid() {

        final HttpServletRequest request = requestWithValidFormSubmitted();
        doReturn("not-a-period")
            .when(request)
            .getParameter("expiryPeriod");

        servlet.doPost(request, response);

        final Dto dto = getForwardedForm(request);
        assertThat(dto.isValid())
            .isEqualTo(false);
        assertThat(dto.getExpiryError())
            .isEqualTo("???passwordreset.settings.expiry-period-invalid???");
    }

    @SneakyThrows
    @Test
    void willValidateThatExpiryPeriodIsSupported() {

        final HttpServletRequest request = requestWithValidFormSubmitted();
        doReturn("YEARS")
            .when(request)
            .getParameter("expiryPeriod");

        servlet.doPost(request, response);

        final Dto dto = getForwardedForm(request);
        assertThat(dto.isValid())
            .isEqualTo(false);
        assertThat(dto.getExpiryError())
            .isEqualTo("???passwordreset.settings.expiry-period-invalid???");
    }

    @SneakyThrows
    @Test
    void willValidateThatMinLengthIsInteger() {

        final HttpServletRequest request = requestWithValidFormSubmitted();
        doReturn("")
            .when(request)
            .getParameter("minLength");

        servlet.doPost(request, response);

        final Dto dto = getForwardedForm(request);
        assertThat(dto.isValid())
            .isEqualTo(false);
        assertThat(dto.getMinLengthError())
            .isEqualTo("???passwordreset.settings.min-length-integer???");
    }

    @SneakyThrows
    @Test
    void willValidateThatMaxLengthIsInteger() {

        final HttpServletRequest request = requestWithValidFormSubmitted();
        doReturn("")
            .when(request)
            .getParameter("maxLength");

        servlet.doPost(request, response);

        final Dto dto = getForwardedForm(request);
        assertThat(dto.isValid())
            .isEqualTo(false);
        assertThat(dto.getMaxLengthError())
            .isEqualTo("???passwordreset.settings.max-length-integer???");
    }

    @SneakyThrows
    @Test
    void validateThatMaxLengthMustExceedMinLength() {

        final HttpServletRequest request = requestWithValidFormSubmitted();
        doReturn("1")
            .when(request)
            .getParameter("maxLength");

        servlet.doPost(request, response);

        final Dto dto = getForwardedForm(request);
        assertThat(dto.isValid())
            .isEqualTo(false);
        assertThat(dto.getMaxLengthError())
            .isEqualTo("???passwordreset.settings.max-length-too-short???");
    }

    @SneakyThrows
    @Test
    void willRedirectWithValidFormDetails() {

        final HttpServletRequest request = requestWithValidFormSubmitted();

        servlet.doPost(request, response);

        verify(response).sendRedirect(REQUEST_URI);
    }

    @SneakyThrows
    @Test
    void willUpdateWithValidFormDetails() {

        final HttpServletRequest request = requestWithValidFormSubmitted();

        servlet.doPost(request, response);

        verify(webManager).logEvent(any(), any());
        assertThat(PasswordResetPlugin.ENABLED.getValue())
            .isEqualTo(true);
        assertThat(PasswordResetPlugin.SERVER.getValue())
            .isEqualTo("https://example.com:7443/passwordreset");
        assertThat(PasswordResetPlugin.SENDER_ADDRESS.getValue())
            .isEqualTo("admin@example.org");
        assertThat(PasswordResetPlugin.SUBJECT.getValue())
            .isEqualTo("email subject");
        assertThat(PasswordResetPlugin.BODY.getValue())
            .isEqualTo("email body");
        assertThat(PasswordResetPlugin.EXPIRY.getValue())
            .isEqualTo(Duration.ofMinutes(42));
    }

    @SneakyThrows
    @Test
    void postWillDoNothingWithReadOnlyProvider() {

        final HttpServletRequest request = requestWithValidFormSubmitted();
        doReturn(true).when(userProvider).isReadOnly();

        servlet.doPost(request, response);

        verify(response).sendRedirect(REQUEST_URI);
        verify(session).setAttribute(eq(FlashMessageTag.ERROR_MESSAGE_KEY), anyString());
        assertThat(PasswordResetPlugin.ENABLED.getValue())
            .isEqualTo(false);
        assertThat(PasswordResetPlugin.SERVER.getValue())
            .isEqualTo("");
    }

    @SneakyThrows
    @Test
    void cancelWillDoNothing() {

        final HttpServletRequest request = requestWithValidFormSubmitted();
        doReturn("Cancel changes")
            .when(request)
            .getParameter("cancel");

        servlet.doPost(request, response);

        verify(response).sendRedirect(REQUEST_URI);
        verify(session).setAttribute(eq(FlashMessageTag.WARNING_MESSAGE_KEY), anyString());
        assertThat(PasswordResetPlugin.ENABLED.getValue())
            .isEqualTo(false);
        assertThat(PasswordResetPlugin.SERVER.getValue())
            .isEqualTo("");
    }

    @SneakyThrows
    @Test
    void testWillSendTestEmail() {

        final HttpServletRequest request = requestWithValidFormSubmitted();
        doReturn(null)
            .when(request)
            .getParameter("update");
        doReturn("Send test email")
            .when(request)
            .getParameter("test");

        doReturn(user)
            .when(webManager)
            .getUser();

        servlet.doPost(request, response);

        verify(passwordResetMailer)
            .sendEmail(user, "for-test-use-only");

        verify(session).setAttribute(eq(FlashMessageTag.SUCCESS_MESSAGE_KEY), anyString());
        verify(requestDispatcher).forward(request, response);
    }
}