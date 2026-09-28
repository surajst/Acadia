import { Alert, Platform } from 'react-native';

/**
 * Tell the user something went wrong, on every platform.
 *
 * <p>`Alert.alert` is a **no-op on React Native Web**. It does not throw and it
 * does not warn -- it returns, and nothing appears. So every failure handled
 * with an Alert is silent on the web build, which is the build QA and the pilot
 * parents actually use. That is how a profile photo upload could fail, be
 * caught, be reported, and still look exactly like success until the page was
 * reloaded.
 *
 * <p>`window.alert` is not pretty. It is, however, impossible to miss, and a
 * blocking dialog is the correct weight for "your picture was not saved". An
 * in-app notice would be nicer and is worth doing; being invisible is not worth
 * keeping in the meantime.
 */
export function notify(title: string, message: string): void {
  if (Platform.OS === 'web') {
    window.alert(`${title}\n\n${message}`);
    return;
  }
  Alert.alert(title, message);
}
