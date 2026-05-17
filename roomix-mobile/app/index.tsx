import { Redirect } from 'expo-router';
import * as SecureStore from 'expo-secure-store';
import { useEffect, useState } from 'react';
import { View, ActivityIndicator } from 'react-native';

export default function Index() {
  const [isLoading, setIsLoading] = useState(true);
  const [hasToken, setHasToken] = useState(false);

  useEffect(() => {
    SecureStore.getItemAsync('access_token').then((token) => {
      setHasToken(!!token);
      setIsLoading(false);
    });
  }, []);

  if (isLoading) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#0f0f23' }}>
        <ActivityIndicator color="#7C3AED" size="large" />
      </View>
    );
  }

  return <Redirect href={hasToken ? '/(tabs)' : '/(auth)/login'} />;
}
